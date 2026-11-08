"""Shared dependency-free GLB geometry writer."""
import json, math, struct

class Glb:
 def __init__(self):
  self.blob=bytearray();self.views=[];self.accessors=[];self.meshes=[];self.nodes=[];self.pending={}
  self.deform=None;self.needs_end_detail=lambda p:False
  def material(name,color,metallic=0,roughness=.6,emissive=None):
   m={'name':name,'pbrMetallicRoughness':{'baseColorFactor':color,'metallicFactor':metallic,'roughnessFactor':roughness},'doubleSided':True}
   if emissive:m['emissiveFactor']=emissive
   return m
  self.materials=[material('paint',[.78,.81,.85,1],.3,.25),material('glass',[.018,.027,.04,1],.22,.10),material('rubber',[.025,.03,.036,1]),material('alloy',[.5,.55,.6,1],.75,.3),material('interior',[.055,.065,.08,1]),material('trim',[.15,.18,.21,1]),material('headlamp',[.85,.94,1,1],emissive=[.35,.4,.5]),material('taillamp',[.55,.025,.02,1],emissive=[.3,.005,.003]),material('screen',[.05,.2,.25,1],emissive=[.015,.07,.09]),material('black_chrome',[.065,.075,.09,1],.65,.28),material('polished_alloy',[.72,.76,.8,1],.95,.19),material('grille_slats',[.026,.030,.036,1],.1,.60),material('plate',[.85,.87,.88,1],.05,.55)]
  self.materials[0]['pbrMetallicRoughness'].update(baseColorFactor=[.90,.92,.95,1],metallicFactor=.15,roughnessFactor=.30)
  self.materials[10]['pbrMetallicRoughness'].update(baseColorFactor=[.88,.90,.93,1],metallicFactor=.80,roughnessFactor=.25)
  self.materials.append(material('grille_cut_faces',[.065,.073,.085,1],.20,.42))
  self.materials[0]['extensions']={'KHR_materials_clearcoat':{'clearcoatFactor':.55,'clearcoatRoughnessFactor':.22}}
 def buffer(self,data,count,bounds=None):
  while len(self.blob)%4:self.blob.append(0)
  off=len(self.blob);self.blob.extend(data);vi=len(self.views);self.views.append({'buffer':0,'byteOffset':off,'byteLength':len(data)})
  a={'bufferView':vi,'componentType':5126,'count':count,'type':'VEC3'}
  if bounds:a.update(min=bounds[0],max=bounds[1])
  self.accessors.append(a);return len(self.accessors)-1
 def mesh(self,name,faces,mat,translation=None,parent=None,merge=True,smooth=False):
  keep={'sculpted_body','hood','trunk','roof','dashboard','windshield','rear_window','side_glass_-1','side_glass_1','room_mirror_housing'}
  if merge and parent is None and translation is None and name not in keep and not name.startswith('mirror_surface'):
   interior=any(name.startswith(p) for p in ['dash','vent','instrument','center_','door_card','cabin_floor','room_mirror'])
   group=('interior_mat_' if interior else 'exterior_mat_')+str(mat)
   if not interior and mat==0:group='exterior_paint'
   self.pending.setdefault((group,mat,smooth),[]).extend(faces)
   return -1
  if self.deform and parent is None and translation is None:
   faces=[[self.deform(point) for point in face] for face in faces]
  # Triangulate warped quads before computing corner-angle-weighted smoothing normals.
  # Zero-area triangles at collapsed crease stations must never enter the normal accumulator.
  triangles=[];averaged={};pos=[];normals=[]
  for face in faces:
   for j in range(1,len(face)-1):
    triangle=[face[0],face[j],face[j+1]]
    a,b,c=triangle;u=[b[i]-a[i] for i in range(3)];v=[c[i]-a[i] for i in range(3)]
    cross=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]]
    area=math.sqrt(sum(t*t for t in cross))
    if area<1e-12:continue
    n=[v/area for v in cross];triangles.append((triangle,n,area))
    if smooth:
     for corner,point in enumerate(triangle):
      e1=[triangle[(corner+1)%3][k]-point[k] for k in range(3)]
      e2=[triangle[(corner+2)%3][k]-point[k] for k in range(3)]
      denominator=math.sqrt(sum(x*x for x in e1)*sum(x*x for x in e2))
      angle=math.acos(max(-1,min(1,sum(a*b for a,b in zip(e1,e2))/denominator)))
      averaged.setdefault(tuple(round(x,7) for x in point),[]).append((n,angle))
  for triangle,n,area in triangles:
   for point in triangle:
    normal=n
    if smooth:
     neighbors=[(other,weight) for other,weight in averaged[tuple(round(x,7) for x in point)] if sum(a*b for a,b in zip(n,other))>.62]
     total=[sum(other[k]*weight for other,weight in neighbors) for k in range(3)];length=math.sqrt(sum(v*v for v in total)) or 1
     normal=[v/length for v in total]
    pos.extend(point);normals.extend(normal)
  coords=list(zip(*[pos[i:i+3] for i in range(0,len(pos),3)]))
  p=self.buffer(struct.pack('<%sf'%len(pos),*pos),len(pos)//3,([min(c) for c in coords],[max(c) for c in coords]))
  n=self.buffer(struct.pack('<%sf'%len(normals),*normals),len(normals)//3)
  mi=len(self.meshes);self.meshes.append({'name':name,'primitives':[{'attributes':{'POSITION':p,'NORMAL':n},'material':mat}]})
  node={'name':name,'mesh':mi}
  if translation:node['translation']=translation
  ni=len(self.nodes);self.nodes.append(node)
  if parent is not None:self.nodes[parent].setdefault('children',[]).append(ni)
  return ni
 def box(self,name,x,y,z,dx,dy,dz,mat=4,parent=None):
  v=[(x+a*dx/2,y+b*dy/2,z+c*dz/2) for a,b,c in [(-1,-1,-1),(1,-1,-1),(1,1,-1),(-1,1,-1),(-1,-1,1),(1,-1,1),(1,1,1),(-1,1,1)]]
  return self.mesh(name,[[v[i] for i in f] for f in [(0,3,2,1),(4,5,6,7),(0,1,5,4),(3,7,6,2),(0,4,7,3),(1,2,6,5)]],mat,parent=parent)
 def beam(self,name,a,b,r,mat=0):
  d=[b[i]-a[i] for i in range(3)];dl=math.sqrt(sum(v*v for v in d))
  if dl>.10 and (self.needs_end_detail(a) or self.needs_end_detail(b)):
   count=math.ceil(dl/.07)
   for i in range(count):self.beam(name,tuple(a[k]+d[k]*i/count for k in range(3)),tuple(a[k]+d[k]*(i+1)/count for k in range(3)),r,mat)
   return
  d=[v/dl for v in d];ref=[0,0,1] if abs(d[2])<.9 else [0,1,0]
  u=[d[1]*ref[2]-d[2]*ref[1],d[2]*ref[0]-d[0]*ref[2],d[0]*ref[1]-d[1]*ref[0]];ul=math.sqrt(sum(v*v for v in u));u=[v/ul*r for v in u]
  v=[d[1]*u[2]-d[2]*u[1],d[2]*u[0]-d[0]*u[2],d[0]*u[1]-d[1]*u[0]]
  rings=[[[p[k]+u[k]*math.cos(i*math.tau/8)+v[k]*math.sin(i*math.tau/8) for k in range(3)] for i in range(8)] for p in [a,b]]
  self.mesh(name,[[rings[0][i],rings[1][i],rings[1][(i+1)%8],rings[0][(i+1)%8]] for i in range(8)]+[rings[0][::-1],rings[1]],mat)
 def cylinder(self,r,w,n=20):
  a=[(r*math.cos(i*math.tau/n),r*math.sin(i*math.tau/n),-w/2) for i in range(n)];b=[(x,y,w/2) for x,y,_ in a]
  return [[a[i],a[(i+1)%n],b[(i+1)%n],b[i]] for i in range(n)]+[a[::-1],b]
 def lettering(self,name,text,x,y,z,height,mat):
  font={'G':['1111','1000','1011','1001','1111'],'R':['1110','1001','1110','1010','1001'],'A':['0110','1001','1111','1001','1001'],'N':['1001','1101','1011','1001','1001'],'D':['1110','1001','1001','1001','1110'],'E':['1111','1000','1110','1000','1111'],'U':['1001','1001','1001','1001','1111'],'H':['1001','1001','1111','1001','1001'],'Y':['1001','1001','0110','0010','0010'],'C':['1111','1000','1000','1000','1111']}
  pixel=height/5;pitch=pixel*6;left=z-len(text)*pitch/2;faces=[]
  for letter,char in enumerate(text):
   for row,line in enumerate(font.get(char,font['H'])):
    for col,on in enumerate(line):
     if on!='1':continue
     yy=y+height/2-row*pixel;zz=left+letter*pitch+col*pixel
     faces.append([(x,yy,zz),(x,yy,zz+pixel*.85),(x,yy-pixel*.85,zz+pixel*.85),(x,yy-pixel*.85,zz)])
  self.mesh(name,faces,mat)
 def rounded_box(self,name,x,y,z,dx,dy,dz,radius,mat):
  half=[dx/2,dy/2,dz/2];faces=[]
  for axis in range(3):
   for sign in [-1,1]:
    others=[i for i in range(3) if i!=axis]
    grid=[]
    for i in range(9):
     row=[]
     for j in range(9):
      p=[0.,0.,0.];p[axis]=sign*half[axis];p[others[0]]=(i/4-1)*half[others[0]];p[others[1]]=(j/4-1)*half[others[1]]
      q=[max(-h+radius,min(h-radius,a)) for a,h in zip(p,half)];v=[a-b for a,b in zip(p,q)];length=math.sqrt(sum(a*a for a in v)) or 1
      row.append(tuple(c+b+a/length*radius for c,b,a in zip([x,y,z],q,v)))
     grid.append(row)
    for i in range(8):
     for j in range(8):
      face=[grid[i][j],grid[i+1][j],grid[i+1][j+1],grid[i][j+1]]
      if sign*(1 if axis!=1 else -1)<0:face.reverse()
      faces.append(face)
  self.mesh(name,faces,mat,smooth=True)
 def surface(self,name,fn,nu,nv,mat,parent=None):
  points=[[fn(i/nu,j/nv) for j in range(nv+1)] for i in range(nu+1)]
  self.mesh(name,[[points[i][j],points[i+1][j],points[i+1][j+1],points[i][j+1]] for i in range(nu) for j in range(nv)],mat,parent=parent,smooth=True)
 def rounded_front(self,name,x,y,z,width,height,radius,mat):
  contour=[]
  for cy,cz,start in [(y+height/2-radius,z+width/2-radius,0),(y+height/2-radius,z-width/2+radius,math.pi/2),(y-height/2+radius,z-width/2+radius,math.pi),(y-height/2+radius,z+width/2-radius,3*math.pi/2)]:
   for j in range(7):
    angle=start+j*math.pi/12
    contour.append((x,cy+radius*math.sin(angle),cz+radius*math.cos(angle)))
  # Dense perimeter and radial rows let trim and lights follow the bumper's compound curve.
  boundary=[]
  for a,b in zip(contour,contour[1:]+contour[:1]):
   count=max(1,math.ceil(math.dist(a,b)/.06))
   boundary.extend(tuple(p+(q-p)*i/count for p,q in zip(a,b)) for i in range(count))
  steps=max(2,math.ceil(max(width,height)/.14));rings=[]
  for row in range(steps+1):
   t=row/steps;rings.append([(x,y+(p[1]-y)*t,z+(p[2]-z)*t) for p in boundary])
  self.mesh(name,[[a[i],b[i],b[(i+1)%len(boundary)],a[(i+1)%len(boundary)]] for a,b in zip(rings,rings[1:]) for i in range(len(boundary))],mat,smooth=True)
 def lathe(self,name,profile,mat,parent=None,segments=48):
  rings=[[(r*math.cos(i*math.tau/segments),r*math.sin(i*math.tau/segments),z) for i in range(segments)] for r,z in profile]
  faces=[[a[i],a[(i+1)%segments],b[(i+1)%segments],b[i]] for a,b in zip(rings,rings[1:]) for i in range(segments)]
  self.mesh(name,faces,mat,parent=parent,smooth=True)
 def save(self,path,extras):
  for (name,mat,smooth),faces in self.pending.items():self.mesh(name,faces,mat,merge=False,smooth=smooth)
  children={c for n in self.nodes for c in n.get('children',[])}
  g={'asset':{'version':'2.0','generator':'Hyuncha procedural vehicles v3'},'extensionsUsed':['KHR_materials_clearcoat'],'scene':0,'scenes':[{'nodes':[i for i in range(len(self.nodes)) if i not in children]}],'nodes':self.nodes,'meshes':self.meshes,'materials':self.materials,'buffers':[{'byteLength':len(self.blob)}],'bufferViews':self.views,'accessors':self.accessors,'extras':extras}
  j=json.dumps(g,separators=(',',':'),ensure_ascii=False).encode();j+=b' '*((-len(j))%4);self.blob.extend(b'\0'*((-len(self.blob))%4))
  result=struct.pack('<III',0x46546c67,2,28+len(j)+len(self.blob))+struct.pack('<II',len(j),0x4e4f534a)+j+struct.pack('<II',len(self.blob),0x004e4942)+self.blob
  path.write_bytes(result);print(f'{path.name}: {len(self.nodes)} nodes, {len(result)} bytes')

