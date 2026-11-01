import math, asyncio, sys, os
from playwright.async_api import async_playwright
from PIL import Image, ImageDraw

def path(x0,x1,yc,amp,cycles=2.0,flat=0.58,n=600):
    pts=[]
    for i in range(n+1):
        t=i/n
        if t<flat:
            k=t/flat; a=amp*(1-k)**0.55
            y=yc-a*math.sin(2*math.pi*cycles*k)
        else: y=yc
        pts.append((x0+(x1-x0)*t,y))
    return "M"+" L".join(f"{x:.2f},{y:.2f}" for x,y in pts), pts[-1]

def svg(bg, size=1024, mode="fg"):
    S=size
    if mode=="fg":   # capa frontal del icono adaptable (108dp, zona segura 66dp)
        safe=S*66/108; m=(S-safe)/2
        x0,x1=m+safe*0.13, S-m-safe*0.15; amp=safe*0.22; sw=safe*0.062
    else:            # icono clásico redondo
        x0,x1=S*0.22,S*0.76; amp=S*0.17; sw=S*0.048
    d,(ex,ey)=path(x0,x1,S/2,amp)
    back = f'<circle cx="{S/2}" cy="{S/2}" r="{S*0.47}" fill="#E5383B"/>' if mode=="legacy" else (f'<rect width="{S}" height="{S}" fill="{bg}"/>' if bg else '')
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{S}" height="{S}" viewBox="0 0 {S} {S}">{back}
<path d="{d}" fill="none" stroke="#fff" stroke-width="{sw:.2f}" stroke-linecap="round" stroke-linejoin="round"/>
<circle cx="{ex:.2f}" cy="{ey:.2f}" r="{sw*0.95:.2f}" fill="#fff"/></svg>'''

async def render(items):
    async with async_playwright() as p:
        b=await p.chromium.launch(); pg=await b.new_page(viewport={'width':1024,'height':1024})
        for name,content in items:
            await pg.set_content(f'<html><body style="margin:0;background:transparent">{content}</body></html>')
            await pg.screenshot(path=name, omit_background=True, clip={'x':0,'y':0,'width':1024,'height':1024})
        await b.close()

asyncio.run(render([('fg.png',svg(None,mode='fg')),('legacy.png',svg(None,mode='legacy'))]))
res=sys.argv[1]
fg=Image.open('fg.png').convert('RGBA'); lg=Image.open('legacy.png').convert('RGBA')
for k,f in {'mdpi':1,'hdpi':1.5,'xhdpi':2,'xxhdpi':3,'xxxhdpi':4}.items():
    p=f'{res}/mipmap-{k}'; os.makedirs(p,exist_ok=True)
    lg.resize((int(48*f),)*2,Image.LANCZOS).save(f'{p}/ic_launcher.png')
    fg.resize((int(108*f),)*2,Image.LANCZOS).save(f'{p}/ic_launcher_foreground.png')
# vista previa
RED=(229,56,59,255)
def masked(shape,s):
    sq=Image.new('RGBA',(1024,1024),RED); sq.alpha_composite(fg)
    m=Image.new('L',(1024,1024),0); dr=ImageDraw.Draw(m)
    # simula la máscara del lanzador: recorta el 108dp a 72dp visibles
    inset=1024*18/108
    if shape=='c': dr.ellipse([inset,inset,1024-inset,1024-inset],fill=255)
    else: dr.rounded_rectangle([inset,inset,1024-inset,1024-inset],radius=190,fill=255)
    o=Image.new('RGBA',(1024,1024),(0,0,0,0)); o.paste(sq,(0,0),m)
    return o.crop((int(inset),int(inset),int(1024-inset),int(1024-inset))).resize((s,s),Image.LANCZOS)
prev=Image.new('RGBA',(1150,400),(243,240,234,255))
prev.alpha_composite(masked('c',300),(40,50)); prev.alpha_composite(masked('r',300),(390,50))
prev.alpha_composite(lg.resize((300,300),Image.LANCZOS),(740,50))
for x,s in [(1060,72)]: pass
prev.save('preview.png')
small=Image.new('RGBA',(400,120),(243,240,234,255))
for x,s in [(20,96),(140,72),(240,48),(310,36)]: small.alpha_composite(masked('c',s),(x,(120-s)//2))
small.save('preview_small.png')
