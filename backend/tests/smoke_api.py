"""Comprueba autenticación, pedidos, inventario, cocina y cobro sobre datos de prueba."""
import json
import os
import urllib.request
import urllib.error
import urllib.parse
import http.cookiejar
import uuid
import concurrent.futures

BASE = os.environ.get('TEST_BASE_URL', 'http://127.0.0.1:8080')
PASSWORD = os.environ.get('DEMO_PASSWORD', 'Cambiar123!')

class Client:
    def __init__(self, email):
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf = self.call('GET', '/csrf')[1]
        body = urllib.parse.urlencode({'email': email, 'password': PASSWORD}).encode()
        status, _ = self.call('POST', '/login', body, 'application/x-www-form-urlencoded')
        assert status == 200, ('login', status)
        self.csrf = self.call('GET', '/csrf')[1]

    def call(self, method, path, data=None, content_type='application/json'):
        headers = {'Content-Type': content_type}
        if method != 'GET':
            headers[self.csrf['headerName']] = self.csrf['token']
        if data is not None and not isinstance(data, bytes):
            data = json.dumps(data).encode()
        request = urllib.request.Request(BASE+'/api'+path, data=data, method=method, headers=headers)
        try:
            response = self.http.open(request, timeout=25)
        except urllib.error.HTTPError as exc:
            response = exc
        raw = response.read()
        if 'json' in response.headers.get('Content-Type', ''):
            return response.status, json.loads(raw)
        return response.status, raw


def run():
    admin = Client('admin@demo.local')
    mozo = Client('mozo@demo.local')
    cocina = Client('cocina@demo.local')
    caja = Client('caja@demo.local')
    assert admin.call('GET', '/me')[1]['rol'] == 'Administrador'
    assert mozo.call('GET', '/insumos')[0] == 403
    assert cocina.call('POST', '/pedidos', {})[0] in (400, 403)
    products = admin.call('GET', '/productos')[1]
    product = products[0]
    insumos = admin.call('GET', '/insumos')[1]
    insumo = next(x for x in insumos if x['nombre'] == 'Porción de '+product['nombre'])
    before = float(insumo['stock_actual'])
    mesa = next(x for x in admin.call('GET', '/mesas')[1] if x['estado'] == 'LIBRE')
    payload = {'solicitudId': str(uuid.uuid4()), 'tipo':'SALON', 'mesaId':mesa['id_mesa'],
               'cliente': None, 'observacion': 'Prueba de integración',
               'items':[{'productoId':product['id'], 'cantidad':2, 'observacion':None}]}
    status, order = mozo.call('POST', '/pedidos', payload)
    assert status == 200, (status,order)
    oid = order['id_pedido']
    assert float(order['total_pedido']) == float(product['precio'])*2
    assert mozo.call('POST','/pedidos',payload)[1]['id_pedido'] == oid
    after = next(x for x in admin.call('GET','/insumos')[1] if x['id_insumo']==insumo['id_insumo'])
    assert float(after['stock_actual']) == before-2
    assert any(o['id_pedido']==oid for o in cocina.call('GET','/pedidos')[1])
    assert mozo.call('PATCH',f'/pedidos/{oid}/estado',{'estado':'LISTO'})[0] == 409
    pay = {'solicitudId':str(uuid.uuid4()),'metodo':'Efectivo','ruc':'','referencia':None}
    assert caja.call('POST',f'/pedidos/{oid}/pago',pay)[0] == 409
    assert cocina.call('PATCH',f'/pedidos/{oid}/estado',{'estado':'EN_PREPARACION'})[0] == 200
    assert cocina.call('PATCH',f'/pedidos/{oid}/estado',{'estado':'LISTO'})[0] == 200
    # Add another item to the same table after the original dishes are ready.
    extra=dict(payload)
    extra['solicitudId']=str(uuid.uuid4())
    extra['items']=[{'productoId':product['id'],'cantidad':1,'observacion':'Adicional'}]
    status,extended=mozo.call('POST','/pedidos',extra)
    assert status==200,(status,extended)
    assert extended['id_pedido']==oid
    assert len(extended['items'])==2
    assert extended['items'][0]['estado_cocina']=='LISTO'
    assert extended['items'][1]['estado_cocina']=='PENDIENTE'
    assert float(extended['total_pedido'])==float(product['precio'])*3
    assert mozo.call('POST','/pedidos',extra)[1]['id_pedido']==oid
    assert mozo.call('PATCH',f'/pedidos/{oid}/estado',{'estado':'ANULADO'})[0]==409
    assert caja.call('POST',f'/pedidos/{oid}/pago',pay)[0]==409
    assert cocina.call('PATCH',f'/pedidos/{oid}/estado',{'estado':'EN_PREPARACION'})[0]==200
    assert cocina.call('PATCH',f'/pedidos/{oid}/estado',{'estado':'LISTO'})[0]==200
    assert mozo.call('POST',f'/pedidos/{oid}/pago',pay)[0] == 403
    status, payment = caja.call('POST',f'/pedidos/{oid}/pago',pay)
    assert status == 200, (status,payment)
    assert caja.call('POST',f'/pedidos/{oid}/pago',pay)[1]['id_pago']==payment['id_pago']
    pay['solicitudId']=str(uuid.uuid4())
    assert caja.call('POST',f'/pedidos/{oid}/pago',pay)[0] == 409
    assert next(x for x in admin.call('GET','/mesas')[1] if x['id_mesa']==mesa['id_mesa'])['estado']=='LIBRE'
    # Create and cancel an independent order, verifying the stock is restored.
    payload.update(solicitudId=str(uuid.uuid4()), tipo='LLEVAR', mesaId=None, cliente='<script>prueba</script>')
    status, take = mozo.call('POST','/pedidos',payload)
    assert status==200,(status,take)
    assert mozo.call('PATCH',f"/pedidos/{take['id_pedido']}/estado",{'estado':'ANULADO'})[0]==200
    restored=next(x for x in admin.call('GET','/insumos')[1] if x['id_insumo']==insumo['id_insumo'])
    assert float(restored['stock_actual'])==before-3
    payload['solicitudId']=str(uuid.uuid4());payload['items'][0]['cantidad']=100
    assert mozo.call('POST','/pedidos',payload)[0]==409
    final=next(x for x in admin.call('GET','/insumos')[1] if x['id_insumo']==insumo['id_insumo'])
    assert float(final['stock_actual'])==before-3
    # Two concurrent requests cannot consume the same remaining stock twice.
    def concurrent_order(name):
        client=Client('mozo@demo.local')
        data={'solicitudId':str(uuid.uuid4()),'tipo':'LLEVAR','mesaId':None,
              'cliente':name,'observacion':None,
              'items':[{'productoId':product['id'],'cantidad':60,'observacion':None}]}
        return client.call('POST','/pedidos',data)
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results=list(pool.map(concurrent_order,['Concurrente A','Concurrente B']))
    assert sorted(status for status,_ in results)==[200,409],results
    successful=next(data for status,data in results if status==200)
    assert mozo.call('PATCH',f"/pedidos/{successful['id_pedido']}/estado",{'estado':'ANULADO'})[0]==200
    final=next(x for x in admin.call('GET','/insumos')[1] if x['id_insumo']==insumo['id_insumo'])
    assert float(final['stock_actual'])==before-3
    status, binary=admin.call('GET','/reportes/inventario.xlsx')
    assert status==200 and binary[:2]==b'PK'
    assert mozo.call('GET','/reportes/inventario.xlsx')[0]==403
    # A mutation without a CSRF token must fail, even with a valid session.
    request=urllib.request.Request(BASE+'/api/logout',data=b'',method='POST')
    try:
        mozo.http.open(request)
        raise AssertionError('Se aceptó una escritura sin CSRF')
    except urllib.error.HTTPError as exc:
        assert exc.code==403
    print('OK: sesiones, permisos, CSRF, persistencia, precios, stock, cocina, cobro, reintentos, anulación, concurrencia, productos adicionales y Excel.')

if __name__=='__main__':
    run()
