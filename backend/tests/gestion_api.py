"""Verifica las funciones de gestión sobre una base EXCLUSIVA de prueba."""
from smoke_api import Client
from datetime import date
import uuid

def check(result, expected=200):
    assert result[0]==expected, result
    return result[1]

def run():
    admin=Client('admin@demo.local')
    mozo=Client('mozo@demo.local')
    check(mozo.call('GET','/gestion'),403)
    check(mozo.call('POST','/gestion/proveedores',{'nombre':'No autorizado','ruc':'12345678901'}),403)
    initial=check(admin.call('GET','/gestion'))
    insumo=check(admin.call('GET','/insumos'))[0]
    iid=insumo['id_insumo']
    before=float(insumo['stock_actual'])
    suffix=uuid.uuid4().hex[:8]
    check(admin.call('POST','/gestion/proveedores',{'nombre':'Proveedor '+suffix,'ruc':str(uuid.uuid4().int)[:11]}))
    g=check(admin.call('GET','/gestion'))
    prov=next(x for x in g['proveedores'] if x['nombre']=='Proveedor '+suffix)
    purchase={'solicitudId':str(uuid.uuid4()),'proveedorId':prov['id_proveedor'],'fecha':date.today().isoformat(),'documento':'Prueba '+suffix,'items':[{'insumoId':iid,'cantidad':2,'costo':3.5}]}
    saved=check(admin.call('POST','/gestion/compras',purchase))
    assert check(admin.call('POST','/gestion/compras',purchase))==saved
    def stock(): return float(next(i for i in check(admin.call('GET','/insumos')) if i['id_insumo']==iid)['stock_actual'])
    assert stock()==before+2
    purchase['documento']='Diferente'
    check(admin.call('POST','/gestion/compras',purchase),409)
    assert stock()==before+2
    # A failure in a multi-line purchase rolls back all changes.
    invalid={**purchase,'solicitudId':str(uuid.uuid4()),'items':[{'insumoId':iid,'cantidad':2,'costo':1},{'insumoId':99999999,'cantidad':1,'costo':1}]}
    check(admin.call('POST','/gestion/compras',invalid),404)
    assert stock()==before+2
    check(admin.call('POST',f'/gestion/insumos/{iid}/movimientos',{'tipo':'MERMA','cantidad':1,'motivo':'Prueba'}))
    assert stock()==before+1
    check(admin.call('POST',f'/gestion/insumos/{iid}/movimientos',{'tipo':'MERMA','cantidad':99999,'motivo':'Prueba'}),409)
    check(admin.call('POST',f'/gestion/insumos/{iid}/movimientos',{'tipo':'AJUSTE','cantidad':before,'motivo':'Conteo físico'}))
    assert stock()==before
    check(admin.call('POST',f'/gestion/insumos/{iid}/movimientos',{'tipo':'ENTRADA','cantidad':-1,'motivo':'Inválido'}),400)
    check(admin.call('POST','/gestion/usuarios',{'nombre':'Inválido','apellido':'Prueba','email':'invalido@test.local','password':'é'*50,'rolId':g['roles'][0]['id_rol']}),409)
    check(admin.call('POST','/gestion/usuarios',{'nombre':'Prueba','apellido':'Usuario','email':suffix+'@test.local','password':'Prueba123!','rolId':next(r['id_rol'] for r in g['roles'] if r['nombre']=='Mozo')}))
    created=next(u for u in check(admin.call('GET','/gestion'))['usuarios'] if u['email']==suffix+'@test.local')
    assert 'password_hash' not in created
    # New user logs in, then must lose the existing session after being disabled.
    import smoke_api
    old=smoke_api.PASSWORD;smoke_api.PASSWORD='Prueba123!'
    temporary=Client(created['email']);smoke_api.PASSWORD=old
    check(admin.call('PATCH',f"/gestion/usuarios/{created['id_usuario']}",{'activo':False}))
    check(temporary.call('GET','/productos'),401)
    check(admin.call('PATCH',f"/gestion/usuarios/{check(admin.call('GET','/me'))['id']}",{'activo':False}),409)
    check(admin.call('POST','/gestion/personal',{'nombre':'Persona '+suffix,'apellido':'Prueba','cargo':'Mozo','turnoId':g['turnos'][0]['id_turno']}))
    person=next(p for p in check(admin.call('GET','/gestion'))['personal'] if p['nombre']=='Persona '+suffix)
    check(admin.call('PUT',f"/gestion/personal/{person['id_personal']}/turno",{'turnoId':g['turnos'][1]['id_turno']}))
    product=check(admin.call('GET','/productos'))[0]
    check(admin.call('PATCH',f"/gestion/productos/{product['id']}/precio",{'precio':product['precio']}))
    old_recipe=[{'insumoId':r['id_insumo'],'cantidad':r['cantidad']} for r in initial['recetas'] if r['id_producto']==product['id']]
    check(admin.call('PUT',f"/gestion/productos/{product['id']}/receta",{'items':old_recipe}))
    invalid_recipe=[{'insumoId':99999999,'cantidad':1}]
    check(admin.call('PUT',f"/gestion/productos/{product['id']}/receta",{'items':invalid_recipe}),404)
    now=check(admin.call('GET','/gestion'))
    assert len([r for r in now['recetas'] if r['id_producto']==product['id']])==len(old_recipe)
    assert now['conciliacion']==[]
    reports=check(admin.call('GET','/gestion/reportes'))
    assert float(reports['resumen']['ventas_total'])>0
    assert reports['productos'] and reports['porMetodo']
    print('OK: permisos, compras, reintentos, rollback, merma, ajuste, usuarios, sesión revocada, personal, turnos, recetas, precios y reportes.')

if __name__=='__main__':run()
