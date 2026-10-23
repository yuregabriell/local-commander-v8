#!/usr/bin/env python3
"""Build three fictional, offline movie/show artwork cards without external dependencies.
All art is original procedural scenery; none represents a licensed film.
"""
import math, pathlib, random, struct, zlib, binascii
OUT=pathlib.Path("app/assets")
OUT.mkdir(parents=True,exist_ok=True)
def png(path, w, h, lines):
    body = zlib.compress(b"".join(b"\x00"+line for line in lines), 7)
    def chunk(tag,data):
        return struct.pack(">I",len(data))+tag+data+struct.pack(">I",binascii.crc32(tag+data)&0xffffffff)
    path.write_bytes(b"\x89PNG\r\n\x1a\n"+chunk(b"IHDR",struct.pack(">IIBBBBB",w,h,8,2,0,0,0))+chunk(b"IDAT",body)+chunk(b"IEND",b""))
def scene(seed):
    w,h=960,540
    schemes=[
        ((30,56,88),(114,146,166),(10,38,55),(31,84,104)),
        ((20,15,53),(82,50,108),(7,15,39),(50,30,69)),
        ((49,28,39),(195,115,95),(26,34,47),(92,76,83)),
    ]
    top,horizon,mountain,water=schemes[seed]
    rng=random.Random(932+seed)
    horizon_y=int(h*.55)
    peak=[h*.38 + math.sin(i*.024+seed)*57 + math.sin(i*.006+seed*3)*38 + math.sin(i*.09)*9 for i in range(w)]
    sun=(int(w*(.7-.09*seed)),int(h*(.29+.03*seed)),int(h*.075))
    for y in range(h):
        row=bytearray()
        for x in range(w):
            z=min(1,y/max(1,horizon_y))
            if y<horizon_y:
                t=z**.75
                col=[int(top[k]*(1-t)+horizon[k]*t) for k in range(3)]
                distance=((x-sun[0])**2+(y-sun[1])**2)**.5
                glow=max(0,1-distance/(sun[2]*3))**3
                warm=(255,207,145)
                col=[min(255,int(v+glow*(warm[k]-v)*.52)) for k,v in enumerate(col)]
                if distance<sun[2]:
                    edge=max(0,min(1,(sun[2]-distance)/3))
                    col=[int(col[k]*(1-edge)+warm[k]*edge) for k in range(3)]
                if y>peak[x]:
                    col=[int(mountain[k]*(.88+.12*z)) for k in range(3)]
            else:
                t=(y-horizon_y)/(h-horizon_y)
                n=(math.sin(x*.08+y*.15)+math.sin(x*.024-y*.26))*3.5
                col=[max(0,min(255,int(water[k]*(1-t*.40)+horizon[k]*t*.14+n))) for k in range(3)]
                if y<peak[x]+(y-horizon_y)*.10:
                    col=[int(col[k]*.78) for k in range(3)]
                reflection=max(0,1-abs(x-sun[0])/(35+t*100))*max(0,1-t)
                col=[min(255,int(col[k]+reflection*(33 if k<2 else 17))) for k in range(3)]
            row.extend(col)
        yield bytes(row)
for seed in range(3):
    target=OUT/f"nova_demo{seed+1}.png"
    png(target,960,540,scene(seed))
    print("Poster de demonstração",target,target.stat().st_size)
# Sample actual mountain lake photograph from Unsplash. No key, freely viewable.
# Fallback to our original landscape artwork when the image host is unavailable.
import urllib.request
url="https://images.unsplash.com/photo-1470770841072-f978cf4d019e?auto=format&fit=crop&w=1920&h=1080&q=82"
try:
    req=urllib.request.Request(url, headers={"User-Agent":"NovaTV/0.4 (example wallpaper)"})
    with urllib.request.urlopen(req,timeout=16) as response: data=response.read(2500000)
    if data[:3]!=b"\xff\xd8\xff" or len(data)<25000: raise ValueError("Foto inválida/pequena")
    (OUT/"nova_landscape.jpg").write_bytes(data)
    print("Foto exemplo: Unsplash",len(data))
except Exception as e:
    # Preserve an offline sample even on GitHub hosts with networking issues.
    import shutil
    shutil.copyfile(OUT/"nova_demo1.png",OUT/"nova_landscape.png")
    print("Sem acesso ao Unsplash; paisagem local usada:",e)
