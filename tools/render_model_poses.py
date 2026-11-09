#!/usr/bin/env python3
"""Create isolated verification-only GLBs for a fixed-camera Filament viewer.
The rotations are camera-equivalent inspection poses; production GLBs are never altered.
"""
import json, math, struct
from pathlib import Path
import numpy as np
root=Path(__file__).resolve().parents[1]
source=root/'app/src/main/assets/models/grandeur.glb'
b=source.read_bytes();size=struct.unpack_from('<I',b,12)[0];g=json.loads(b[20:20+size]);binary=b[20+size:]
out=root/'verification/models';out.mkdir(parents=True,exist_ok=True)
for label,yaw,tilt in [('front',-45,13),('side',0,8),('rear',135,13)]:
 scene=json.loads(json.dumps(g));ids=scene['scenes'][0]['nodes']
 ay=math.radians(yaw);ax=math.radians(tilt)
 ry=np.array([[math.cos(ay),0,math.sin(ay),0],[0,1,0,0],[-math.sin(ay),0,math.cos(ay),0],[0,0,0,1]])
 rx=np.array([[1,0,0,0],[0,math.cos(ax),-math.sin(ax),0],[0,math.sin(ax),math.cos(ax),0],[0,0,0,1]])
 matrix=rx@ry
 ni=len(scene['nodes']);scene['nodes'].append({'name':'inspection_pose','matrix':matrix.T.flatten().tolist(),'children':ids});scene['scenes'][0]['nodes']=[ni]
 j=json.dumps(scene,separators=(',',':'),ensure_ascii=False).encode();j+=b' '*((-len(j))%4)
 result=struct.pack('<III',0x46546c67,2,20+len(j)+len(binary))+struct.pack('<II',len(j),0x4e4f534a)+j+binary
 (out/(label+'.glb')).write_bytes(result)
 (out/(label+'.json')).write_text(json.dumps([{'name':label,'base':{'view.taa.enabled':True,'view.ssao.enabled':True,'view.antiAliasing':'FXAA','viewer.groundPlaneEnabled':False,'viewer.skyboxEnabled':False,'viewer.cameraFocalLength':43,'viewer.cameraAperture':16,'viewer.cameraSpeed':125,'viewer.cameraISO':100}}]))
