# -*- coding: utf-8 -*-
from __future__ import print_function
import os, sys, json, time, ssl, uuid, ctypes, threading, subprocess, shutil
import urllib.request, urllib.error, webbrowser
from pathlib import Path
from collections import deque

VERSION = "0.8-desktop"
RELAY = "https://local-commander-relay-yure.amberdill2.chatgpt.site"
PLUGIN_URL = "https://chatgpt.com/plugins/plugins_6acd2b0fb8a48191b53aaf4c28044e1a"

ROOT = Path(__file__).resolve().parent.parent
RUNTIME = ROOT / "runtime"
RUNTIME.mkdir(exist_ok=True)
APPDATA = Path(os.environ.get("APPDATA") or str(Path.home())) / "LocalCommanderV8"
APPDATA.mkdir(parents=True, exist_ok=True)
CRED_FILE = APPDATA / "device.dpapi"
CONFIG_FILE = APPDATA / "config.json"
LOG_FILE = APPDATA / "agent.log"
FIRST_RUN = RUNTIME / "first_run.flag"
STOP = threading.Event()
PAUSED = threading.Event()
ACTIVITY = deque(maxlen=8)
PROCESSES = {}
OUTPUTS = {}
OUTPUT_LOCK = threading.Lock()

try:
    import psutil
except Exception:
    psutil = None
try:
    import pyautogui
except Exception:
    pyautogui = None

# ---------- logging ----------
def log(msg, event=None):
    ts = time.strftime("%H:%M:%S")
    ACTIVITY.appendleft((ts, event or str(msg)[:40], str(msg)))
    try:
        with open(str(LOG_FILE), "a", encoding="utf-8", errors="replace") as f:
            f.write(time.strftime("%Y-%m-%d %H:%M:%S") + " " + str(msg) + "\n")
    except Exception:
        pass

# ---------- Windows helpers / DPAPI ----------
class DATA_BLOB(ctypes.Structure):
    _fields_ = [("cbData", ctypes.c_ulong), ("pbData", ctypes.POINTER(ctypes.c_ubyte))]

def _blob(data):
    buf = ctypes.create_string_buffer(data)
    return DATA_BLOB(len(data), ctypes.cast(buf, ctypes.POINTER(ctypes.c_ubyte))), buf

def protect(data):
    if os.name != "nt": return data
    i, keep = _blob(data); o = DATA_BLOB()
    if not ctypes.windll.crypt32.CryptProtectData(ctypes.byref(i), "Local Commander", None, None, None, 0, ctypes.byref(o)):
        raise ctypes.WinError()
    try: return ctypes.string_at(o.pbData, o.cbData)
    finally: ctypes.windll.kernel32.LocalFree(o.pbData)

def unprotect(data):
    if os.name != "nt": return data
    i, keep = _blob(data); o = DATA_BLOB()
    if not ctypes.windll.crypt32.CryptUnprotectData(ctypes.byref(i), None, None, None, None, 0, ctypes.byref(o)):
        raise ctypes.WinError()
    try: return ctypes.string_at(o.pbData, o.cbData)
    finally: ctypes.windll.kernel32.LocalFree(o.pbData)

def load_config():
    default = {"access_mode":"user", "allowed_roots":[str(Path.home())], "start_minimized":False}
    try:
        if CONFIG_FILE.exists():
            default.update(json.loads(CONFIG_FILE.read_text(encoding="utf-8")))
    except Exception: pass
    return default

def save_config(c):
    CONFIG_FILE.write_text(json.dumps(c, indent=2), encoding="utf-8")

CONFIG = load_config()

def load_creds():
    try:
        if CRED_FILE.exists():
            return json.loads(unprotect(CRED_FILE.read_bytes()).decode("utf-8"))
    except Exception as e:
        log("Credential read failed: %s" % e)
    return None

def save_creds(c):
    CRED_FILE.write_bytes(protect(json.dumps(c).encode("utf-8")))

def console_hwnd():
    try: return ctypes.windll.kernel32.GetConsoleWindow()
    except Exception: return 0

def minimize():
    h=console_hwnd()
    if h: ctypes.windll.user32.ShowWindow(h, 6)

def restore():
    h=console_hwnd()
    if h:
        ctypes.windll.user32.ShowWindow(h, 9)
        ctypes.windll.user32.SetForegroundWindow(h)

# ---------- HTTP ----------
def post_json(path, payload=None, headers=None, timeout=30):
    data=json.dumps(payload or {}).encode("utf-8")
    req=urllib.request.Request(RELAY+path, data=data, method="POST")
    req.add_header("Content-Type","application/json")
    req.add_header("User-Agent","LocalCommander/%s"%VERSION)
    for k,v in (headers or {}).items(): req.add_header(k,v)
    with urllib.request.urlopen(req, timeout=timeout, context=ssl.create_default_context()) as r:
        raw=r.read()
        return r.getcode(), json.loads(raw.decode("utf-8")) if raw else {}

def headers(c):
    return {"Authorization":"Bearer "+c["device_token"],"X-Local-Commander-Device":c["device_id"]}

# ---------- permissions ----------
def allowed_roots():
    if CONFIG.get("access_mode")=="full":
        return [Path(p) for p in _drive_roots()]
    return [Path(p).expanduser().resolve() for p in CONFIG.get("allowed_roots") or [str(Path.home())]]

def _drive_roots():
    if os.name!="nt": return ["/"]
    roots=[]
    mask=ctypes.windll.kernel32.GetLogicalDrives()
    for i in range(26):
        if mask & (1<<i):
            roots.append(chr(65+i)+":\\")
    return roots

def resolve_path(p):
    p = p or str(Path.home())
    x=Path(os.path.expandvars(os.path.expanduser(str(p))))
    try: x=x.resolve()
    except Exception: x=Path(os.path.abspath(str(x)))
    if CONFIG.get("access_mode")=="full": return x
    xs=os.path.normcase(str(x))
    for root in allowed_roots():
        rs=os.path.normcase(str(root))
        try:
            if os.path.commonpath([xs,rs])==rs: return x
        except Exception: pass
    raise PermissionError("Path not authorized by Local Commander profile")

# ---------- tools ----------
def lc_status(**kw):
    return {"name":"Local Commander","version":VERSION,"computer":os.environ.get("COMPUTERNAME",""),
            "os":os.name,"state":"paused" if PAUSED.is_set() else "online",
            "profile":CONFIG.get("access_mode"),"workspaces":[str(x) for x in allowed_roots()],
            "relay":RELAY,
            "permissions":{"files.read":True,"files.write":True,"shell.run":True,"git":True,
                           "browser.open":True,"browser.automation":True,"desktop":True}}

def lc_list_dir(path=None,directory=None,**kw):
    p=resolve_path(path or directory)
    items=[]
    for x in sorted(p.iterdir(), key=lambda z:(not z.is_dir(),z.name.lower()))[:1000]:
        try:
            s=x.stat()
            items.append({"name":x.name,"path":str(x),"type":"dir" if x.is_dir() else "file",
                          "size":None if x.is_dir() else s.st_size,"modified":s.st_mtime})
        except Exception:
            items.append({"name":x.name,"path":str(x),"type":"unknown"})
    return {"path":str(p),"items":items}

def lc_project_tree(path=None,max_depth=3,max_items=500,**kw):
    root=resolve_path(path); count=[0]
    def walk(p,d):
        if d>int(max_depth) or count[0]>=int(max_items): return []
        out=[]
        try: children=sorted(p.iterdir(),key=lambda z:(not z.is_dir(),z.name.lower()))
        except Exception as e: return [{"error":str(e)}]
        for x in children:
            if count[0]>=int(max_items): break
            count[0]+=1
            n={"name":x.name,"path":str(x),"type":"dir" if x.is_dir() else "file"}
            if x.is_dir(): n["children"]=walk(x,d+1)
            out.append(n)
        return out
    return {"path":str(root),"tree":walk(root,0)}

def lc_read_file(path=None,start_line=1,max_lines=500,offset=None,limit=None,**kw):
    p=resolve_path(path)
    if p.stat().st_size>5_000_000: raise ValueError("File too large for direct text read")
    text=p.read_text(encoding="utf-8",errors="replace")
    if offset is not None:
        o=max(0,int(offset)); n=min(int(limit or 6000),200000)
        return {"path":str(p),"text":text[o:o+n],"next_offset":o+n if o+n<len(text) else None,"total_chars":len(text)}
    lines=text.splitlines(True); s=max(1,int(start_line)); n=min(max(1,int(max_lines)),3000)
    return {"path":str(p),"text":"".join(lines[s-1:s-1+n]),"start_line":s,
            "next_line":s+n if s-1+n<len(lines) else None,"total_lines":len(lines)}

def lc_write_file(path=None,content="",append=False,create_dirs=True,**kw):
    p=resolve_path(path)
    if create_dirs: p.parent.mkdir(parents=True,exist_ok=True)
    with open(str(p),"a" if append else "w",encoding="utf-8",errors="replace") as f: f.write(str(content))
    return {"ok":True,"path":str(p),"bytes":p.stat().st_size}

def lc_replace_text(path=None,old_text=None,new_text=None,old=None,new=None,count=-1,**kw):
    p=resolve_path(path); s=p.read_text(encoding="utf-8",errors="replace")
    a=old_text if old_text is not None else old; b=new_text if new_text is not None else new
    if a is None: raise ValueError("old_text required")
    hits=s.count(str(a))
    if not hits: return {"ok":False,"replacements":0,"path":str(p)}
    d=s.replace(str(a),str(b or ""),int(count)) if int(count)>=0 else s.replace(str(a),str(b or ""))
    p.write_text(d,encoding="utf-8")
    return {"ok":True,"replacements":hits if int(count)<0 else min(hits,int(count)),"path":str(p)}

def lc_delete_path(path=None,recursive=False,**kw):
    p=resolve_path(path)
    if p.is_dir():
        if recursive: shutil.rmtree(str(p))
        else: p.rmdir()
    else: p.unlink()
    return {"ok":True,"path":str(p)}

def lc_search_text(path=None,query=None,pattern=None,recursive=True,max_results=100,**kw):
    root=resolve_path(path); needle=str(query if query is not None else pattern or "")
    if not needle: raise ValueError("query required")
    files=[root] if root.is_file() else (root.rglob("*") if recursive else root.glob("*"))
    out=[]
    for f in files:
        if len(out)>=int(max_results): break
        try:
            if not f.is_file() or f.stat().st_size>2_000_000: continue
            for i,line in enumerate(f.read_text(encoding="utf-8",errors="ignore").splitlines(),1):
                if needle.lower() in line.lower():
                    out.append({"path":str(f),"line":i,"text":line[:800]})
                    if len(out)>=int(max_results): break
        except Exception: pass
    return {"query":needle,"results":out,"count":len(out)}

def lc_run(command=None,cmd=None,cwd=None,timeout=60,**kw):
    command=command if command is not None else cmd
    cp=subprocess.run(command if isinstance(command,list) else str(command),
        cwd=str(resolve_path(cwd)) if cwd else None,shell=not isinstance(command,list),
        stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=min(max(int(timeout),1),900),
        universal_newlines=True,errors="replace")
    return {"returncode":cp.returncode,"output":cp.stdout[-200000:]}

def lc_start_process(command=None,cmd=None,cwd=None,**kw):
    command=command if command is not None else cmd
    oid=str(uuid.uuid4()); logp=APPDATA/("process-"+oid+".log")
    fh=open(str(logp),"w",encoding="utf-8",errors="replace")
    p=subprocess.Popen(str(command),cwd=str(resolve_path(cwd)) if cwd else None,shell=True,
                       stdout=fh,stderr=subprocess.STDOUT,universal_newlines=True)
    PROCESSES[oid]={"p":p,"fh":fh,"log":logp}
    return {"process_id":oid,"pid":p.pid,"log":str(logp)}

def lc_process_logs(process_id=None,offset=0,limit=6000,**kw):
    x=PROCESSES.get(str(process_id))
    if not x: raise ValueError("Unknown process_id")
    try:x["fh"].flush()
    except:pass
    t=x["log"].read_text(encoding="utf-8",errors="replace") if x["log"].exists() else ""
    o=max(0,int(offset)); n=min(max(100,int(limit)),12000)
    return {"text":t[o:o+n],"next_offset":o+n if o+n<len(t) else None,"running":x["p"].poll() is None,"returncode":x["p"].poll()}

def lc_stop_process(process_id=None,**kw):
    x=PROCESSES.get(str(process_id))
    if not x: raise ValueError("Unknown process_id")
    x["p"].terminate()
    try:x["p"].wait(2)
    except: x["p"].kill()
    try:x["fh"].close()
    except:pass
    return {"ok":True}

def _git(args,cwd=None):
    g=shutil.which("git")
    if not g: raise RuntimeError("Git is not installed")
    cp=subprocess.run([g]+args,cwd=str(resolve_path(cwd)) if cwd else str(Path.home()),
                      stdout=subprocess.PIPE,stderr=subprocess.STDOUT,universal_newlines=True,errors="replace")
    return {"returncode":cp.returncode,"output":cp.stdout[-150000:]}
def lc_git_status(path=None,cwd=None,**kw): return _git(["status","--short","--branch"],cwd or path)
def lc_git_diff(path=None,cwd=None,staged=False,**kw): return _git(["diff"]+(["--staged"] if staged else []),cwd or path)
def lc_git_log(path=None,cwd=None,limit=20,**kw): return _git(["log","--oneline","-n",str(min(int(limit),100))],cwd or path)
def lc_git_commit(path=None,cwd=None,message=None,add_all=False,**kw):
    wd=cwd or path
    if add_all:_git(["add","-A"],wd)
    return _git(["commit","-m",str(message or "Local Commander commit")],wd)

# ---------- browser ----------
_BROWSER=None
def _browser_page():
    global _BROWSER
    from playwright.sync_api import sync_playwright
    if _BROWSER is None:
        pw=sync_playwright().start()
        browser=pw.chromium.launch(headless=False)
        page=browser.new_page(viewport={"width":1280,"height":800})
        _BROWSER=(pw,browser,page)
    return _BROWSER[2]

def lc_browser_open(url=None,**kw):
    if not url: raise ValueError("url required")
    try:
        p=_browser_page(); p.goto(str(url),wait_until="domcontentloaded",timeout=45000)
        return {"ok":True,"url":p.url,"title":p.title(),"automation":True}
    except Exception as e:
        webbrowser.open(str(url))
        return {"ok":True,"url":str(url),"automation":False,"warning":str(e)[:500]}

def lc_browser_snapshot(**kw):
    try:
        p=_browser_page()
        return {"url":p.url,"title":p.title(),"text":p.locator("body").inner_text(timeout=5000)[:30000]}
    except Exception as e:return {"ok":False,"error":str(e)}

def lc_browser_click(selector=None,text=None,**kw):
    p=_browser_page()
    if selector:p.locator(str(selector)).first.click()
    elif text:p.get_by_text(str(text),exact=False).first.click()
    else:raise ValueError("selector or text required")
    return {"ok":True,"url":p.url}

def lc_browser_fill(selector=None,value=None,text=None,**kw):
    p=_browser_page()
    if not selector: raise ValueError("selector required")
    p.locator(str(selector)).first.fill(str(value if value is not None else text or ""))
    return {"ok":True}

def lc_browser_press(selector=None,key=None,**kw):
    p=_browser_page()
    if selector:p.locator(str(selector)).first.press(str(key or "Enter"))
    else:p.keyboard.press(str(key or "Enter"))
    return {"ok":True}

def lc_browser_close(**kw):
    global _BROWSER
    if _BROWSER:
        try:_BROWSER[1].close(); _BROWSER[0].stop()
        except:pass
        _BROWSER=None
    return {"ok":True}

# ---------- desktop ----------
def lc_desktop_screenshot(path=None,**kw):
    p=resolve_path(path) if path else APPDATA/("screenshot-%d.png"%int(time.time()))
    if pyautogui:
        im=pyautogui.screenshot(); im.save(str(p)); return {"ok":True,"path":str(p),"size":p.stat().st_size}
    raise RuntimeError("pyautogui is not available")

def lc_desktop_move(x=None,y=None,**kw):
    if pyautogui: pyautogui.moveTo(int(x),int(y)); return {"ok":True}
    ctypes.windll.user32.SetCursorPos(int(x),int(y)); return {"ok":True}

def lc_desktop_click(x=None,y=None,button="left",**kw):
    if pyautogui: pyautogui.click(x=int(x) if x is not None else None,y=int(y) if y is not None else None,button=str(button)); return {"ok":True}
    if x is not None and y is not None: ctypes.windll.user32.SetCursorPos(int(x),int(y))
    down,up=(0x0008,0x0010) if str(button).lower()=="right" else (0x0002,0x0004)
    ctypes.windll.user32.mouse_event(down,0,0,0,0);ctypes.windll.user32.mouse_event(up,0,0,0,0)
    return {"ok":True}

def lc_desktop_type(text="",**kw):
    if not pyautogui: raise RuntimeError("pyautogui unavailable")
    pyautogui.write(str(text),interval=0.01); return {"ok":True,"chars":len(str(text))}

def lc_desktop_hotkey(keys=None,**kw):
    if not pyautogui: raise RuntimeError("pyautogui unavailable")
    if isinstance(keys,str): keys=[k.strip() for k in keys.replace("+",",").split(",") if k.strip()]
    pyautogui.hotkey(*[str(k) for k in (keys or [])]); return {"ok":True}

def lc_approval_status(**kw): return {"status":"approved","profile":CONFIG.get("access_mode"),"workspaces":[str(x) for x in allowed_roots()]}

def lc_output_read(output_id=None,offset=0,limit=3000,**kw):
    with OUTPUT_LOCK:item=OUTPUTS.get(str(output_id))
    if not item: raise ValueError("output not found")
    raw=item["text"];o=max(0,int(offset));n=min(max(100,int(limit)),6000)
    return {"text":raw[o:o+n],"next_offset":o+n if o+n<len(raw) else None,"total_chars":len(raw)}

def unsupported(**kw): return {"unsupported":True}

TOOLS={
"lc_status":lc_status,"lc_list_dir":lc_list_dir,"lc_project_tree":lc_project_tree,"lc_read_file":lc_read_file,
"lc_write_file":lc_write_file,"lc_replace_text":lc_replace_text,"lc_delete_path":lc_delete_path,"lc_search_text":lc_search_text,
"lc_run":lc_run,"lc_start_process":lc_start_process,"lc_process_logs":lc_process_logs,"lc_stop_process":lc_stop_process,
"lc_git_status":lc_git_status,"lc_git_diff":lc_git_diff,"lc_git_log":lc_git_log,"lc_git_commit":lc_git_commit,
"lc_browser_open":lc_browser_open,"lc_browser_snapshot":lc_browser_snapshot,"lc_browser_click":lc_browser_click,
"lc_browser_fill":lc_browser_fill,"lc_browser_press":lc_browser_press,"lc_browser_close":lc_browser_close,
"lc_desktop_screenshot":lc_desktop_screenshot,"lc_desktop_move":lc_desktop_move,"lc_desktop_click":lc_desktop_click,
"lc_desktop_type":lc_desktop_type,"lc_desktop_hotkey":lc_desktop_hotkey,"lc_approval_status":lc_approval_status,
"lc_output_read":lc_output_read,"lc_load_project_context":unsupported,"lc_ensure_project_memory":unsupported,
"lc_android_mkdir":unsupported,"lc_android_ui":unsupported,"lc_android_swipe":unsupported,"lc_android_back":unsupported,"lc_android_home":unsupported}

def compact(v):
    raw=json.dumps(v,ensure_ascii=True,default=str)
    if len(raw)<=6000:return {"ok":True,"result":v}
    oid=str(uuid.uuid4())
    with OUTPUT_LOCK:OUTPUTS[oid]={"text":raw[:250000],"time":time.time()}
    return {"ok":True,"preview":raw[:2500],"output_id":oid,"total_chars":len(raw),"truncated":True}

def execute_call(call):
    tool=call.get("tool");args=call.get("arguments") or {}
    if PAUSED.is_set():
        return {"ok":False,"error":"Local Commander is paused by the user"}
    fn=TOOLS.get(tool)
    if not fn:return {"ok":False,"error":"Unknown action: "+str(tool)}
    try:
        log("running "+tool,tool)
        out=compact(fn(**args));log("completed "+tool,tool+" ✓");return out
    except Exception as e:
        log("error %s: %s"%(tool,e),tool+" ✕")
        return {"ok":False,"error":str(e)[:1800]}

# ---------- pairing ----------
def pair():
    _,d=post_json("/api/relay/start",{"name":os.environ.get("COMPUTERNAME") or "Windows PC"})
    c={"device_id":d["device_id"],"device_token":d["device_token"]};save_creds(c)
    clear()
    print("LOCAL COMMANDER V8 - PRIMEIRA CONEXAO\n")
    print("Codigo:",d.get("code",""))
    print("Pagina:",d.get("connect_url",RELAY))
    print("\nO navegador sera aberto para confirmar este computador.")
    try:webbrowser.open(d.get("connect_url",RELAY))
    except:pass
    return c

# ---------- dashboard ----------
def clear(): os.system("cls" if os.name=="nt" else "clear")
def line(label,value,width=46):
    return "│ %-12s %-31s│"%(label,str(value)[:31])

def draw(state="ONLINE",creds=None):
    clear()
    color = "●"
    print("┌──────────────────────────────────────────────┐")
    print("│              LOCAL COMMANDER V8              │")
    print("├──────────────────────────────────────────────┤")
    print(line("Status", color+" "+state))
    print(line("Computador",os.environ.get("COMPUTERNAME","Windows PC")))
    print(line("Relay","CONNECTED" if creds else "NOT PAIRED"))
    print(line("Plugin","Local Commander"))
    print(line("Perfil",CONFIG.get("access_mode","user")))
    roots="; ".join(str(x) for x in allowed_roots())
    print(line("Acesso",roots))
    print("├──────────────────────────────────────────────┤")
    print("│ Atividade recente                             │")
    if ACTIVITY:
        for ts,ev,msg in list(ACTIVITY)[:4]:
            print("│ %-8s %-35s│"%(ts,(ev[:35])))
    else:
        print("│ Nenhuma acao ainda.                           │")
    print("├──────────────────────────────────────────────┤")
    print("│ Ctrl+Alt+M  Minimizar                         │")
    print("│ Ctrl+Alt+P  Pausar / Retomar                  │")
    print("│ Ctrl+Alt+D  Atualizar dashboard               │")
    print("│ Ctrl+Alt+H  Abrir guia de conexao             │")
    print("│ Ctrl+Alt+Q  Encerrar                          │")
    print("└──────────────────────────────────────────────┘")
    print("\nMantendo conexao com ChatGPT...",flush=True)

# ---------- hotkeys ----------
HOTKEYS={1:(0x0001|0x0002,ord("M")),2:(0x0001|0x0002,ord("P")),3:(0x0001|0x0002,ord("D")),
         4:(0x0001|0x0002,ord("H")),5:(0x0001|0x0002,ord("Q"))}

def hotkey_loop(creds):
    if os.name!="nt": return
    u=ctypes.windll.user32
    for i,(mods,key) in HOTKEYS.items():u.RegisterHotKey(None,i,mods,key)
    msg=ctypes.wintypes.MSG()
    try:
        while not STOP.is_set():
            r=u.GetMessageW(ctypes.byref(msg),None,0,0)
            if r<=0: break
            if msg.message==0x0312:
                hid=int(msg.wParam)
                if hid==1:minimize()
                elif hid==2:
                    if PAUSED.is_set(): PAUSED.clear();log("resumed","PAUSE → ONLINE")
                    else:PAUSED.set();log("paused","ONLINE → PAUSED")
                    draw("PAUSADO" if PAUSED.is_set() else "ONLINE",creds)
                elif hid==3:draw("PAUSADO" if PAUSED.is_set() else "ONLINE",creds)
                elif hid==4:
                    webbrowser.open((ROOT/"docs"/"conectar.html").as_uri())
                elif hid==5:STOP.set()
    finally:
        for i in HOTKEYS:u.UnregisterHotKey(None,i)

def first_run_help():
    if FIRST_RUN.exists():
        try:webbrowser.open((ROOT/"docs"/"conectar.html").as_uri())
        except:pass
        try:FIRST_RUN.unlink()
        except:pass

def main():
    if os.name!="nt":
        print("Local Commander V8 Desktop is intended for Windows.");return 2
    first_run_help()
    creds=load_creds()
    if not creds:
        creds=pair()
    h=headers(creds)
    threading.Thread(target=hotkey_loop,args=(creds,),daemon=True).start()
    if CONFIG.get("start_minimized"):minimize()
    draw("ONLINE",creds)
    delay=2
    last_draw=time.time()
    while not STOP.is_set():
        try:
            _,d=post_json("/api/relay/poll",{},h,25)
            call=d.get("call")
            if call:
                result=execute_call(call)
                for attempt in range(3):
                    try:
                        post_json("/api/relay/result",{"call_id":call.get("call_id"),"result":result},h,20);break
                    except Exception as e:
                        log("result retry: "+str(e))
                        time.sleep(1+attempt)
                draw("PAUSADO" if PAUSED.is_set() else "ONLINE",creds)
            elif time.time()-last_draw>30:
                draw("PAUSADO" if PAUSED.is_set() else "ONLINE",creds);last_draw=time.time()
            delay=2;time.sleep(2)
        except urllib.error.HTTPError as e:
            if e.code in (401,410):
                try:CRED_FILE.unlink()
                except:pass
                creds=pair();h=headers(creds);draw("ONLINE",creds);continue
            log("HTTP "+str(e.code),"OFFLINE")
            draw("OFFLINE",creds);time.sleep(min(delay,30));delay=min(delay*2,30)
        except KeyboardInterrupt:
            STOP.set()
        except Exception as e:
            log("connection: "+str(e),"OFFLINE")
            draw("OFFLINE",creds);time.sleep(min(delay,30));delay=min(delay*2,30)
    try:lc_browser_close()
    except:pass
    clear();print("Local Commander V8 encerrado.")
    return 0

if __name__=="__main__":
    sys.exit(main())
