#!/usr/bin/env python3
"""Deterministic low-poly GLBs, no modelling dependencies.
Physical dimensions come from CarSpec.kt; body/cabin/eye landmarks are authored per vehicle.
Axes: +X forward, +Y up, +Z right, origin at rear axle, metres.
"""
import json, math, re, struct, argparse
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'app/src/main/assets/models'
SOURCE=(ROOT/'app/src/main/java/com/team/hyuncha/data/CarSpec.kt').read_text()
DESIGNS={
 'AVANTE':dict(id='avante',body=.90,roof=1.43,eye=1.15,eyeA=1.11,radius=.31,cabinRear=-.58,roofRear=.05,roofFront=1.32,windshield=2.20),
 'GRANDEUR':dict(id='grandeur',body=.98,roof=1.48,eye=1.18,eyeA=None,radius=.34,cabinRear=-.47,roofRear=.25,roofFront=1.78,windshield=2.50),
 'TUCSON':dict(id='tucson',body=1.12,roof=1.68,eye=1.38,eyeA=1.21,radius=.36,cabinRear=-.75,roofRear=-.45,roofFront=1.48,windshield=2.35),
 'SANTA_FE':dict(id='santafe',body=1.16,roof=1.77,eye=1.47,eyeA=1.24,radius=.37,cabinRear=-.84,roofRear=-.65,roofFront=1.51,windshield=2.38),
}
from glb_geometry import Glb

def physical(key):
 match=re.search(r'val '+key+r' = CarSpec\((.*?)\n    \)',SOURCE,re.S)
 if not match:raise ValueError(key)
 s=match[1]
 return dict(name=re.search(r'name = "([^"]+)"',s)[1],suv='CarType.SUV' in s,**{k:float(re.search(r'\b'+k+r' = ([\d.]+)',s)[1]) for k in ['lengthM','widthM','wheelBaseM','rearOverhangM']})

def generate(key,design):
 p=physical(key);d=dict(design);g=Glb();box=g.box;mesh=g.mesh;beam=g.beam
 length=p['lengthM'];w=p['widthM']/2;wb=p['wheelBaseM'];rear=p['rearOverhangM'];front=length-rear
 body=d['body'];roof=d['roof'];eye=d['eye'];ea=d['eyeA'] if d['eyeA'] is not None else length*.45-rear;r=d['radius']
 cr=d['cabinRear'];rr=d['roofRear'];rf=d['roofFront'];wf=d['windshield'];suv=p['suv'];belt=body+.005
 if key=='GRANDEUR':
  def smoothstep(t):
   t=max(0,min(1,t));return t*t*(3-2*t)
  def bend_ends(point):
   x,y,z=point
   if x>front-.65:
    setback=.17*(abs(z)/(w*.97))**2+.070*smoothstep((y-.67)/.32)+.055*smoothstep((.34-y)/.20)
    x-=setback*smoothstep((x-front+.65)/.65)
   elif x<-rear+.60:
    setback=.18*(abs(z)/(w*.97))**2+.115*smoothstep((y-.72)/.30)+.070*smoothstep((.40-y)/.25)
    x+=setback*smoothstep((-rear+.60-x)/.60)
   return (x,y,z)
  g.deform=bend_ends
  g.needs_end_detail=lambda point:point[0]>front-.65 or point[0]<-rear+.60
 bp=1.0 if key=='GRANDEUR' else .37
 def bezier(a,b,c,d,t):return (1-t)**3*a+3*(1-t)**2*t*b+3*(1-t)*t*t*c+t**3*d
 def front_x(t):return bezier(wf,wf-.10,rf+.12,rf,t)
 def front_y(t):return bezier(belt,belt+.16,roof-.05,roof-.05,t)
 def rear_x(t):return bezier(cr,cr+.06,rr-.10,rr,t)
 def rear_y(t):return bezier(belt,belt+.14,roof-.05,roof-.05,t)
 def roof_y(t):return roof-.05+.05*math.sin(math.pi*t)
 # Distinct landmarks, roofline, bonnet and rear hatch for each body style; no scaled sedan shortcut.
 sections=[(-rear,w*.84,.17,body*.98),(-rear+.16,w*.97,.17,body*.99),(cr,w,.17,body),(wf,w,.17,body),(front-.20,w*.97,.17,body*.9),(front,w*.84,.16,body*.86)]
 # Add wheel-arch samples while retaining a low centre floor and the exact outer body bounds.
 base_sections=sections
 xs={row[0] for row in base_sections}
 xs.update(-rear+length*i/64 for i in range(65))
 for i in range(25):
  angle=math.pi*i/48
  xs.add(front-.20+.20*math.sin(angle));xs.add(-rear+.20-.20*math.sin(angle))
 arch_radius=r*1.10
 for axle in [0,wb]:
  for angle in [math.pi*i/20 for i in range(21)]:xs.add(axle+arch_radius*math.cos(angle))
 sections=[]
 for x in sorted(xs):
  if not (-rear<=x<=front):continue
  left,right=next((a,b) for a,b in zip(base_sections,base_sections[1:]) if a[0]-1e-9<=x<=b[0]+1e-9)
  t=(x-left[0])/(right[0]-left[0]);t=(1-math.cos(math.pi*t))/2;sections.append((x,)+tuple(a+(b-a)*t for a,b in zip(left[1:],right[1:])))
 rings=[]
 for x,hw,lo,hi in sections:
  if x>=front-.20:hw=w*(.84+.13*math.sqrt(max(0,1-((x-front+.20)/.20)**2)))
  if x<=-rear+.20:hw=w*(.84+.13*math.sqrt(max(0,1-((x+rear-.20)/.20)**2)))
  outer_bottom=lo+.12
  for axle in [0,wb]:
   distance=abs(x-axle)
   if distance<=arch_radius:outer_bottom=max(outer_bottom,r+math.sqrt(max(0,arch_radius**2-distance**2)))
  outer_bottom=min(outer_bottom,hi-.15)
  middle=min(hi-.12,max(outer_bottom+.012,hi-.27))
  # A shallow pressed lower-door crease is part of the shell, not an overlapping overlay.
  door_t=max(0,min(1,(x-.44)/(wf-.64)))
  inset=.045*math.sin(math.pi*door_t)**2 if .44<x<wf-.20 else 0.
  crease=.42-.10*door_t
  low_crease=max(outer_bottom,crease-.028)
  high_crease=max(outer_bottom,crease+.120)
  mid=max(outer_bottom,hi-.27)
  def side_width(y):
   levels=[(.28,hw*.985),(crease-.028,hw*.985),(crease+.120,hw*.985-inset),(hi-.27,hw*.985),(hi-.10,hw)]
   if y<=levels[0][0]:return levels[0][1]
   for (ya,za),(yb,zb) in zip(levels,levels[1:]):
    if y<=yb:
     t=max(0,min(1,(y-ya)/(yb-ya)))
     return za+(zb-za)*t
   return hw
  ys=[outer_bottom,low_crease,high_crease,mid,hi-.10]
  right_side=[(x,y,side_width(y)) for y in ys]
  left_side=[(x,y,-side_width(y)) for y in reversed(ys)]
  rings.append([(x,lo,-hw*.78),(x,lo,hw*.78),(x,lo+.025,hw*.94)]+right_side+[(x,hi-.035,hw*.94),(x,hi+.005,hw*.915),(x,hi+.015,0),(x,hi+.005,-hw*.915),(x,hi-.035,-hw*.94)]+left_side+[(x,lo+.025,-hw*.94)])
 faces=[];hood_faces=[];trunk_faces=[]
 for si,(a,b) in enumerate(zip(rings,rings[1:])):
  for i in range(19):
   if i in [9,10] and sections[si][0]>=cr and sections[si+1][0]<=wf:continue
   face=[a[i],a[(i+1)%19],b[(i+1)%19],b[i]]
   if i in [9,10] and sections[si][0]>=wf:hood_faces.append(face)
   elif i in [9,10] and sections[si+1][0]<=cr:trunk_faces.append(face)
   else:faces.append(face)
 # End caps are curved surfaces, with enough vertices to round them in width and height.
 for edge in [rings[0],rings[-1]]:
  center=(edge[0][0],.55,0);cap_rings=[]
  for j in range(13):
   t=j/12;cap_rings.append([tuple(a+(b-a)*t for a,b in zip(center,p)) for p in edge])
  cap_faces=[[a[i],b[i],b[(i+1)%19],a[(i+1)%19]] for a,b in zip(cap_rings,cap_rings[1:]) for i in range(19)]
  faces.extend([face[::-1] for face in cap_faces] if center[0]<0 else cap_faces)
 mesh('sculpted_body',faces,0,smooth=True)
 # Subdivided bonnet shares its perimeter with the shell; two pressed creases remain geometric.
 hood_stations=[row for row in sections if row[0]>=wf]
 hood_u=[-1,-.86,-.68,-.60,-.56,-.52,-.42,-.2,0,.2,.42,.52,.56,.60,.68,.86,1]
 hood_grid=[]
 for x,hw,lo,hi in hood_stations:
  if x>=front-.20:hw=w*(.84+.13*math.sqrt(max(0,1-((x-front+.20)/.20)**2)))
  longitudinal=(x-wf)/(front-wf)
  row=[]
  for u in hood_u:
   ridge=.020*math.exp(-((abs(u)-(.57-.07*longitudinal))/.09)**2)*math.sin(math.pi*longitudinal)
   crown=(.010+.015*math.sin(math.pi*longitudinal))*(1-u*u)
   row.append((x,hi+.005+crown+ridge,hw*.915*u))
  hood_grid.append(row)
 hood_faces=[[a[j],b[j],b[j+1],a[j+1]] for a,b in zip(hood_grid,hood_grid[1:]) for j in range(len(hood_u)-1)]
 mesh('hood',hood_faces,0,smooth=True)
 if trunk_faces:mesh('trunk',trunk_faces,0,smooth=True)
 g.surface('roof',lambda u,v:(rr+(rf-rr)*u,roof_y(u)+.025*math.sin(math.pi*v), (v*2-1)*w*.77),16,16,0)
 g.surface('panoramic_roof',lambda u,v:(rr+.12+(rf-rr-.27)*u,roof_y((.12+(rf-rr-.27)*u)/(rf-rr))+.005+.025*math.sin(math.pi*(.1+.8*v)),(v*2-1)*w*.61),12,12,1)
 g.surface('windshield',lambda u,v:(front_x(u)+.025*math.sin(math.pi*u)*math.sin(math.pi*v),front_y(u)+(.012*math.sin(math.pi*u)+.025*u)*math.sin(math.pi*v),(v*2-1)*w*(.9-.13*u)),20,24,1)
 g.surface('rear_window',lambda u,v:(rear_x(u)-.025*math.sin(math.pi*u)*math.sin(math.pi*v),rear_y(u)+.025*u*math.sin(math.pi*v),(v*2-1)*w*(.9-.13*u)),16,24,1)
 if suv:
  box('rear_hatch',-rear+.10,body*.88,0,.10,.22,w*1.68,0)
  for side in [-1,1]:box('roof_rail',.4,roof+.045,side*w*.68,1.7,.045,.035,5)
 for side in [-1,1]:
  z=side*w*.915
  def side_point(u,v,offset=0):
   return (rear_x(v)+(front_x(v)-rear_x(v))*u,rear_y(v)+(front_y(v)-rear_y(v))*u+.05*math.sin(math.pi*u)*v,side*(w*(.915-.145*v)+.012*math.sin(math.pi*u)*math.sin(math.pi*v)+offset))
  g.surface('side_glass_'+str(side),side_point,32,16,1)
  for label,xfn,yfn,band in [('a_pillar',front_x,front_y,.085),('c_pillar',rear_x,rear_y,.20)]:
   is_front=label=='a_pillar'
   def pillar_side(t,a):
    portion=band*a/(front_x(t)-rear_x(t))
    return side_point(1-portion if is_front else portion,t,.006)
   g.surface(label+str(side),pillar_side,24,6,0)
   def pillar_wrap(t,a):
    outer=side_point(1 if is_front else 0,t,.006)
    inner=(xfn(t),yfn(t)+.003,side*(w*(.90-.13*t)-.003))
    return tuple(p+(q-p)*a for p,q in zip(outer,inner))
   g.surface(label+'_return'+str(side),pillar_wrap,24,3,0)
  # All window surrounds follow the glazing surface; straight beams protrude through curved glass.
  def vertical_window_band(x,t,across,width):
   u=(x+(across*2-1)*width-rear_x(t))/(front_x(t)-rear_x(t))
   return side_point(u,t,.004)
  g.surface('b_pillar'+str(side),lambda t,a:vertical_window_band(bp,t,a,.035),16,2,2)
  if key=='GRANDEUR':
   for i in range(10):
    a=i/10;b=(i+1)/10
    def quarter(t):
     xx=.15+.30*t;u=(xx-rear_x(t))/(front_x(t)-rear_x(t))
     return side_point(u,t,.003)
    beam('rear_quarter_divider',quarter(a),quarter(b),.009,2)
  box('door_card_'+str(side),(wf+cr)/2,body-.16,side*w*.83,wf-cr-.12,.22,.05,4)
  
  def shell_z(xx,yy):
   index=next(i for i in range(len(sections)-1) if sections[i][0]<=xx<=sections[i+1][0])
   widths=[]
   for ring in rings[index:index+2]:
    points=ring[2:10]
    if yy<=points[0][1]:widths.append(points[0][2]);continue
    for a,b in zip(points,points[1:]):
     if a[1]<=yy<=b[1] and b[1]>a[1]:
      widths.append(a[2]+(b[2]-a[2])*(yy-a[1])/(b[1]-a[1]));break
    else:widths.append(points[-1][2])
   t=(xx-sections[index][0])/(sections[index+1][0]-sections[index][0])
   return widths[0]+(widths[1]-widths[0])*t
  for handle_x in [bp+.15,.08 if key=='GRANDEUR' else cr+.17]:
   hz=shell_z(handle_x,belt-.09)
   box('door_handle_recess_'+str(side),handle_x,belt-.09,side*(hz+.002),.19,.032,.006,2)
   box('door_handle_'+str(side),handle_x,belt-.09,side*(hz+.006),.17,.025,.005,0)
  for seam_x in [bp,wf-.13]:
   for i in range(24):
    a=.30+(belt-.33)*i/24;b=.30+(belt-.33)*(i+1)/24
    beam('door_seam_'+str(side),(seam_x,a,side*(shell_z(seam_x,a)+.004)),(seam_x,b,side*(shell_z(seam_x,b)+.004)),.0014,5)
  beam('window_lower_trim_'+str(side),(cr,belt-.005,z),(wf,belt-.005,z),.007,10)
  
  for i in range(32):
   a=i/32;b=(i+1)/32
   beam('window_upper_trim_'+str(side),side_point(a,1,.008),side_point(b,1,.008),.004,10)
  mx=wf-.25 if key=='GRANDEUR' else ea+.67;mz=side*(w+.13);my=eye-.11
  mount=[(wf-.04,belt-.035,side*w*.90),(wf-.18,belt+.09,side*w*.90),(mx+.18,my-.045,mz),(mx+.10,my-.065,mz)]
  mesh('mirror_mount'+str(side),[mount],2)
  mirror_rings=[]
  for xx,sy,sz in [(mx+.012,.90,.90),(mx+.065,1.,1.),(mx+.19,.95,.92),(mx+.29,.55,.40)]:
   mirror_rings.append([(xx,my+.07*sy*math.sin(i*math.tau/24),mz+.12*sz*math.cos(i*math.tau/24)) for i in range(24)])
  upper=[];lower=[]
  for a,b in zip(mirror_rings,mirror_rings[1:]):
   for i in range(24):
    face=[a[i],a[(i+1)%24],b[(i+1)%24],b[i]]
    (upper if math.sin((i+.5)*math.tau/24)>-.25 else lower).append(face)
  mesh('mirror_housing_'+str(side),upper,0,smooth=True)
  mesh('mirror_lower_'+str(side),lower,2,smooth=True)
  box('mirror_surface_'+str(side),mx+.003,my,mz,.008,.105,.16,3)
  lamp_z=side*w*.60
  if suv:
   box('headlamp_'+str(side),front-.04,body*.73,lamp_z,.035,.17,.24,6)
   box('taillamp_'+str(side),-rear+.015,body*.78,side*w*.80,.025,.24,.10,7)
  elif key!='GRANDEUR':
   box('headlamp_'+str(side),front-.05,body*.73,lamp_z,.035,.09,.40,6)
   box('taillamp_'+str(side),-rear+.015,body*.72,lamp_z,.025,.09,.40,7)
 if key=='GRANDEUR':
  g.rounded_front('front_grille',front+.012,.53,0,1.49,.41,.055,2)
  facets=[[],[]]
  for row in range(5):
   yy=.385+row*.068
   for col in range(10):
    zz=-.63+col*.14
    contour=[(front+.015,yy,zz-.069),(front+.015,yy+.027,zz),(front+.015,yy,zz+.069),(front+.015,yy-.027,zz)]
    center=(front+.027,yy,zz)
    for i in range(4):facets[i%2].append([center,contour[i],contour[(i+1)%4]])
  mesh('grille_diamond_dark',facets[0],11)
  mesh('grille_diamond_highlight',facets[1],13)
  g.rounded_front('drl_housing',front+.002,.805,0,1.58,.047,.018,2)
  g.rounded_front('drl_strip',front+.004,.805,0,1.53,.014,.006,6)
  for side in [-1,1]:
   wraps=[]
   for i in range(13):
    zz=.765+(.90-.765)*i/12
    t=max(0,min(1,(zz-w*.84)/(w*.13)))
    xx=front-.20+.20*math.sqrt(max(0,1-t*t))
    wraps.append((xx+.004,.805,side*zz))
   for a,b in zip(wraps,wraps[1:]):
    beam('drl_corner_housing',a,b,.012,2);beam('drl_corner_light',(a[0]+.003,a[1],a[2]),(b[0]+.003,b[1],b[2]),.005,6)
   g.rounded_front('front_lamp_border',front+.040,.535,side*.63,.215,.315,.040,5)
   g.rounded_front('front_lamp_housing',front+.042,.535,side*.63,.195,.295,.035,9)
   for yy in [.51,.61]:g.rounded_front('front_lamp_lens',front+.045,yy,side*.63,.105,.045,.014,6)
  g.rounded_front('lower_intake',front+.003,.245,0,1.46,.070,.025,2)
  beam('bumper_chrome',(front+.004,.185,-.75),(front+.004,.185,.75),.010,10)
  badge_x=front-.24;badge_y=body*.90+.023
  ring=[]
  for i in range(40):
   a=i*math.tau/40;b=(i+1)*math.tau/40
   ring.append([(badge_x+.043*math.cos(a),badge_y,.073*math.sin(a)),(badge_x+.043*math.cos(b),badge_y,.073*math.sin(b)),(badge_x+.034*math.cos(b),badge_y+.001,.062*math.sin(b)),(badge_x+.034*math.cos(a),badge_y+.001,.062*math.sin(a))])
  mesh('hood_badge',ring,10)
  for z1,z2 in [(-.028,-.012),(.012,.028)]:beam('hood_badge_h',(badge_x-.023,badge_y+.002,z1),(badge_x+.023,badge_y+.002,z2),.004,10)
  beam('hood_badge_cross',(badge_x,badge_y+.002,-.02),(badge_x,badge_y+.002,.02),.004,10)
 else:box('front_grille',front+.001,body*.53,0,.018,.25 if suv else .17,w*1.5,2)
 beam('rear_light_bar',(-rear-.001,body*.89,-w*.79),(-rear-.001,body*.89,w*.79),.012,7)
 if key=='GRANDEUR':
  g.rounded_front('rear_plate_surround',-rear-.004,.45,0,.49,.14,.02,9)
  g.rounded_front('rear_plate',-rear-.007,.45,0,.45,.11,.012,12)
  g.lettering('plate_mark','HYUNCHA',-rear-.009,.45,0,.030,2)
  g.lettering('rear_lettering','GRANDEUR',-rear-.005,.72,0,.045,10)
  g.rounded_front('rear_valance',-rear-.004,.25,0,1.49,.10,.025,2)
  for side in [-1,1]:g.rounded_front('rear_reflector',-rear-.007,.30,side*.66,.15,.02,.008,7)
  beam('rear_bumper_trim',(-rear-.006,.19,-.72),(-rear-.006,.19,.72),.009,10)
  for side in [-1,1]:
   points=[]
   for i in range(12):
    zz=.74+(.90-.74)*i/11;t=max(0,min(1,(zz-w*.84)/(w*.13)))
    xx=-rear+.20-.20*math.sqrt(max(0,1-t*t))
    points.append((xx-.004,body*.89,side*zz))
   for a,b in zip(points,points[1:]):beam('rear_light_corner',a,b,.01,7)
 else:box('rear_plate',-rear+.01,body*.5,0,.015,.12,.38,3)
 box('underbody',length/2-rear,.155,0,length-.30,.035,w*1.55,2)
 for axle,x in [('rear',0),('front',wb)]:
  for side,z in [('left',-w+.11),('right',w-.11)]:
   pivot=len(g.nodes);g.nodes.append({'name':f'{axle}_{side}_steer','translation':[x,r,z]})
   spin=len(g.nodes);g.nodes.append({'name':f'{axle}_{side}_spin','children':[]});g.nodes[pivot]['children']=[spin]
   g.lathe(f'{axle}_{side}_tire',[(r*.67,-.10),(r*.88,-.10),(r*.98,-.083),(r,-.055),(r,-.037),(r*.986,-.032),(r,-.027),(r,-.005),(r*.986,0),(r,.005),(r,.027),(r*.986,.032),(r,.037),(r,.055),(r*.98,.083),(r*.88,.10),(r*.67,.10)],2,parent=spin)
   g.lathe(f'{axle}_{side}_rim',[(r*.67,-.106),(r*.73,-.106),(r*.76,-.095),(r*.76,.095),(r*.73,.106),(r*.67,.106)],10,parent=spin)
   mesh(f'{axle}_{side}_brake_disc',g.cylinder(r*.63,.175,40),9,parent=spin)
   mesh(f'{axle}_{side}_hub',g.cylinder(r*.17,.218,24),10,parent=spin)
   spokes=[];bolts=[];badge=[]
   for k in range(10):
    angle=k*math.tau/10
    for zz in [-.109,.109]:
     # Broad diamond-cut faces with ten dark wedge openings, matching the reference wheel.
     spokes.append([(r*radius*math.cos(angle+offset),r*radius*math.sin(angle+offset),zz) for radius,offset in [(.18,-.12),(.47,-.075),(.74,-.18),(.74,.14),(.47,.12),(.18,.12)]])
   for zz in [-.112,.112]:
    for k in range(5):
     angle=k*math.tau/5;cx=r*.16*math.cos(angle);cy=r*.16*math.sin(angle)
     for j in range(12):
      a=j*math.tau/12;b=(j+1)*math.tau/12
      bolts.append([(cx,cy,zz),(cx+.008*math.cos(a),cy+.008*math.sin(a),zz),(cx+.008*math.cos(b),cy+.008*math.sin(b),zz)])
    for j in range(24):
     a=j*math.tau/24;b=(j+1)*math.tau/24
     badge.append([(0,0,zz),(r*.10*math.cos(a),r*.10*math.sin(a),zz),(r*.10*math.cos(b),r*.10*math.sin(b),zz)])
   mesh(f'{axle}_{side}_spokes',spokes,10,parent=spin)
   mesh(f'{axle}_{side}_lug_recesses',bolts+badge,2,parent=spin)
   logos=[]
   for zz in [-.113,.113]:
    for j in range(24):
     a=j*math.tau/24;b=(j+1)*math.tau/24
     logos.append([(r*.075*math.cos(a),r*.043*math.sin(a),zz),(r*.075*math.cos(b),r*.043*math.sin(b),zz),(r*.063*math.cos(b),r*.031*math.sin(b),zz),(r*.063*math.cos(a),r*.031*math.sin(a),zz)])
    for x1,x2 in [(-.010,-.006),(.006,.010)]:logos.append([(x1-.004,-.010,zz),(x2+.004,.010,zz),(x2+.009,.010,zz),(x1+.001,-.010,zz)])
    logos.append([(-.010,-.002,zz),(.010,-.002,zz),(.010,.002,zz),(-.010,.002,zz)])
   mesh(f'{axle}_{side}_wheel_badge',logos,10,parent=spin)
   for zz in [-.086,.086]:box(f'{axle}_{side}_brake_caliper',-r*.40,.035,zz,.07,.15,.025,5,parent=pivot)
 # A common cockpit with eye-relative dashboard, wheel and display placements.
 wheel_x=ea+.48;dash_x=wheel_x+.30;dash_y=eye-.24
 box('dashboard',dash_x,dash_y,0,.28,.14,w*1.76,4)
 box('dash_upper',dash_x+.04,dash_y+.085,0,.12,.035,w*1.65,5)
 for z in [-w*.68,-.1,.2,w*.66]:
  box('vent',dash_x-.15,dash_y+.03,z,.015,.055,.18,2)
  for k in [-1,0,1]:box('vent_slat',dash_x-.163,dash_y+.03+k*.013,z,.008,.004,.17,3)
 box('instrument_cluster',dash_x-.16,eye-.135,-.37,.03,.09,.34,2)
 box('instrument_display',dash_x-.18,eye-.135,-.37,.008,.06,.27,8)
 box('center_display',dash_x-.165,eye-.125,.18,.025,.13,.25,8)
 box('center_console',ea-.18,eye-.50,.17,.85,.38,.3,4)
 box('room_mirror_housing',ea+.57,eye+.10,-.12,.03,.17,.42,4)
 beam('room_mirror_stem',(ea+.59,roof-.025,-.12),(ea+.59,eye+.17,-.12),.012,4)
 box('cabin_floor',(cr+wf)/2,.28,0,wf-cr,.025,w*1.7,4)
 wheel=len(g.nodes);g.nodes.append({'name':'steering_wheel','translation':[wheel_x,eye-.30,-.37],'children':[]})
 faces=[]
 for i in range(40):
  for j in range(8):
   face=[]
   for a,b in [(i,j),(i+1,j),(i+1,j+1),(i,j+1)]:
    t=a*math.tau/40;u=b*math.tau/8;rad=.18+.018*math.cos(u);face.append((.018*math.sin(u),rad*math.cos(t),rad*math.sin(t)))
   faces.append(face)
 mesh('steering_rim',faces,4,parent=wheel,smooth=True)
 box('steering_hub',0,0,0,.05,.07,.1,5,parent=wheel)
 box('steering_spoke_left',0,0,-.11,.025,.022,.14,3,parent=wheel)
 box('steering_spoke_right',0,0,.11,.025,.022,.14,3,parent=wheel)
 box('steering_spoke_bottom',0,-.1,0,.025,.14,.025,3,parent=wheel)
 profile=dict(id=d['id'],name=p['name'],asset=d['id']+'.glb',lengthM=length,widthM=w*2,wheelBaseM=wb,rearOverhangM=rear,eyeForwardM=ea,eyeHeightM=eye,mirrorForwardM=mx,mirrorHeightM=my,mirrorRightM=w+.13,wheelRadiusM=r,roofHeightM=roof,bodyHeightM=body,dashboardForwardM=dash_x,instrumentForwardM=dash_x-.185,instrumentHeightM=eye-.135,monitorForwardM=dash_x-.19,monitorHeightM=eye-.125,rearCameraHeightM=1.1 if suv else .95,origin='rear axle',mirrorCollision='visual mirrors excluded, matching existing bodyRect')
 g.save(OUT/profile['asset'],profile)
 if key=='GRANDEUR':(OUT/'sedan.glb').write_bytes((OUT/profile['asset']).read_bytes())
 return profile

OUT.mkdir(parents=True,exist_ok=True)
parser=argparse.ArgumentParser();parser.add_argument('--car',choices=DESIGNS.keys());args=parser.parse_args()
profiles=[]
for key,design in DESIGNS.items():
 if args.car is None or args.car==key:profiles.append(generate(key,design))
 else:
  path=OUT/(design['id']+'.glb');raw=path.read_bytes();length=struct.unpack_from('<I',raw,12)[0]
  profiles.append(json.loads(raw[20:20+length])['extras'])
(OUT/'vehicle_visuals.json').write_text(json.dumps(profiles,ensure_ascii=False,indent=2)+'\n')
fields=['lengthM','widthM','wheelBaseM','rearOverhangM','eyeForwardM','eyeHeightM','mirrorForwardM','mirrorHeightM','mirrorRightM','wheelRadiusM','roofHeightM','bodyHeightM','dashboardForwardM','instrumentForwardM','instrumentHeightM','monitorForwardM','monitorHeightM','rearCameraHeightM']
k='''// Generated by tools/generate_sedan.py. Visual heights are authored game-camera values, not manufacturer specifications.
package com.team.hyuncha.render3d

import com.team.hyuncha.data.CarSpec
import kotlin.math.abs

internal data class VehicleVisualProfile(val asset: String,\n'''+',\n'.join('    val '+f+': Double' for f in fields)+''',
)
internal object VehicleVisuals {
    private val profiles = mapOf(
'''
for p in profiles:k+='        '+json.dumps(p['name'],ensure_ascii=False)+' to VehicleVisualProfile('+json.dumps(p['asset'])+', '+', '.join(repr(round(p[f],8)) for f in fields)+'),\n'
k+='''    )
    fun forCar(car: CarSpec) = profiles.getValue(car.name)
    fun supports(car: CarSpec): Boolean {
        val p=profiles[car.name] ?: return false
        return abs(p.lengthM-car.lengthM)<1e-6 && abs(p.widthM-car.widthM)<1e-6 && abs(p.wheelBaseM-car.wheelBaseM)<1e-6 && abs(p.rearOverhangM-car.rearOverhangM)<1e-6
    }
}
'''
(ROOT/'app/src/main/java/com/team/hyuncha/render3d/VehicleVisuals.kt').write_text(k)
