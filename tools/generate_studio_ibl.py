#!/usr/bin/env python3
"""Authored softbox environment for GLB quality inspection; no downloaded scene or textures."""
from pathlib import Path
import numpy as np
root=Path(__file__).resolve().parents[1];out=root/'verification/studio';out.mkdir(parents=True,exist_ok=True)
w,h=512,256
y,x=np.mgrid[0:h,0:w];longitude=(x+.5)/w*2*np.pi-np.pi;latitude=np.pi/2-(y+.5)/h*np.pi
up=np.sin(latitude)
base=.12+.10*np.maximum(up,0)+.04*np.maximum(-up,0)
rgb=np.stack([base*.97,base,base*1.04],axis=2)
def band(center,width,low,high,strength,tint):
 angle=np.abs(np.angle(np.exp(1j*(longitude-center))))
 edge=lambda value:np.clip(value,0,1)**2*(3-2*np.clip(value,0,1))
 mask=edge((width-angle)/.12)*edge((latitude-low)/.10)*edge((high-latitude)/.10)
 rgb[:]+=mask[:,:,None]*np.array(tint)[None,None,:]*strength
band(-.8,.48,.13,1.10,2.8,(1,.98,.95))
band(2.25,.55,.18,1.15,2.0,(.94,.97,1))
# A broad, uninterrupted overhead source avoids the stripy building reflections in the old HDRI.
ceiling=np.clip((up-.66)/.23,0,1)**2
rgb+=ceiling[:,:,None]*np.array([1.8,1.8,1.75])
maximum=np.max(rgb,axis=2);mantissa,exponent=np.frexp(maximum);scale=mantissa*256/maximum
rgbe=np.zeros((h,w,4),dtype=np.uint8);rgbe[:,:,:3]=np.clip(rgb*scale[:,:,None],0,255).astype(np.uint8);rgbe[:,:,3]=(exponent+128).astype(np.uint8)
with (out/'studio.hdr').open('wb') as f:
 f.write(f'#?RADIANCE\nFORMAT=32-bit_rle_rgbe\n\n-Y {h} +X {w}\n'.encode())
 for row in rgbe:
  f.write(bytes([2,2,w>>8,w&255]))
  for channel in range(4):
   for start in range(0,w,128):
    block=row[start:start+128,channel].tobytes();f.write(bytes([len(block)])+block)
print(out/'studio.hdr')
