# -*- coding: utf-8 -*-
"""
Local Commander V8.1 features.

This module intentionally contains no LLM client and requires no API key.
ChatGPT remains the planner/brain; this module provides stronger local execution,
persistent task state, terminal sessions, clipboard, and binary transfer helpers.
"""
from __future__ import annotations

import base64
import ctypes
import hashlib
import json
import os
import queue
import shutil
import subprocess
import threading
import time
import uuid
from pathlib import Path
from typing import Any, Dict, Optional

APPDATA = Path(os.environ.get("APPDATA") or str(Path.home())) / "LocalCommanderV8"
TASK_DIR = APPDATA / "tasks"
CHECKPOINT_DIR = APPDATA / "checkpoints"
TASK_DIR.mkdir(parents=True, exist_ok=True)
CHECKPOINT_DIR.mkdir(parents=True, exist_ok=True)

_TERMINALS: Dict[str, dict] = {}
_TERMINALS_LOCK = threading.Lock()


def _now() -> float:
    return time.time()


def _sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while True:
            chunk = f.read(1024 * 1024)
            if not chunk:
                break
            h.update(chunk)
    return h.hexdigest()


# ---------------------------------------------------------------------------
# Persistent terminal sessions
# ---------------------------------------------------------------------------

def _reader_loop(session_id: str) -> None:
    session = _TERMINALS.get(session_id)
    if not session:
        return
    p = session["process"]
    q = session["queue"]
    try:
        while True:
            chunk = p.stdout.readline()
            if chunk in (b"", ""):
                break
            if isinstance(chunk, bytes):
                text = chunk.decode("utf-8", errors="replace")
            else:
                text = chunk
            q.put(text)
    finally:
        q.put(None)


def terminal_start(command: Optional[str] = None, cwd: Optional[str] = None, shell: str = "cmd") -> dict:
    if os.name != "nt":
        executable = os.environ.get("SHELL") or "/bin/sh"
        args = [executable]
    else:
        if shell.lower() in ("powershell", "pwsh"):
            executable = shutil.which("powershell.exe") or "powershell.exe"
            args = [executable, "-NoLogo", "-NoProfile"]
        else:
            executable = os.environ.get("COMSPEC") or "cmd.exe"
            args = [executable, "/Q"]

    p = subprocess.Popen(
        args,
        cwd=cwd or None,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        bufsize=0,
    )
    sid = str(uuid.uuid4())
    session = {
        "id": sid,
        "process": p,
        "queue": queue.Queue(),
        "buffer": "",
        "created_at": _now(),
        "cwd": cwd,
        "shell": shell,
    }
    with _TERMINALS_LOCK:
        _TERMINALS[sid] = session
    threading.Thread(target=_reader_loop, args=(sid,), daemon=True).start()

    if command:
        terminal_send(sid, command)

    return {"session_id": sid, "pid": p.pid, "shell": shell, "cwd": cwd}


def terminal_send(session_id: str, text: str, newline: bool = True) -> dict:
    with _TERMINALS_LOCK:
        session = _TERMINALS.get(str(session_id))
    if not session:
        raise ValueError("Unknown terminal session")
    p = session["process"]
    if p.poll() is not None:
        raise RuntimeError("Terminal session is no longer running")
    payload = str(text) + ("\r\n" if newline else "")
    p.stdin.write(payload.encode("utf-8", errors="replace"))
    p.stdin.flush()
    return {"ok": True, "session_id": session_id, "chars": len(payload)}


def _drain(session: dict) -> None:
    q = session["queue"]
    while True:
        try:
            item = q.get_nowait()
        except queue.Empty:
            break
        if item is None:
            break
        session["buffer"] += item
        if len(session["buffer"]) > 500_000:
            session["buffer"] = session["buffer"][-500_000:]


def terminal_read(session_id: str, offset: int = 0, limit: int = 6000) -> dict:
    with _TERMINALS_LOCK:
        session = _TERMINALS.get(str(session_id))
    if not session:
        raise ValueError("Unknown terminal session")
    _drain(session)
    text = session["buffer"]
    offset = max(0, int(offset))
    limit = min(max(100, int(limit)), 12000)
    p = session["process"]
    return {
        "session_id": session_id,
        "text": text[offset:offset + limit],
        "next_offset": offset + limit if offset + limit < len(text) else None,
        "total_chars": len(text),
        "running": p.poll() is None,
        "returncode": p.poll(),
        "pid": p.pid,
    }


def terminal_stop(session_id: str) -> dict:
    with _TERMINALS_LOCK:
        session = _TERMINALS.get(str(session_id))
    if not session:
        raise ValueError("Unknown terminal session")
    p = session["process"]
    if p.poll() is None:
        p.terminate()
        try:
            p.wait(3)
        except Exception:
            p.kill()
    return {"ok": True, "session_id": session_id, "returncode": p.poll()}


def terminal_summary() -> dict:
    out = []
    with _TERMINALS_LOCK:
        items = list(_TERMINALS.items())
    for sid, s in items:
        _drain(s)
        p = s["process"]
        out.append({
            "session_id": sid,
            "pid": p.pid,
            "running": p.poll() is None,
            "shell": s["shell"],
            "created_at": s["created_at"],
        })
    return {"count": len(out), "sessions": out}


# ---------------------------------------------------------------------------
# Clipboard (Windows)
# ---------------------------------------------------------------------------

CF_UNICODETEXT = 13
GMEM_MOVEABLE = 0x0002


def _clipboard_api():
    user32 = ctypes.windll.user32
    kernel32 = ctypes.windll.kernel32
    user32.OpenClipboard.argtypes = [ctypes.c_void_p]
    user32.OpenClipboard.restype = ctypes.c_bool
    user32.CloseClipboard.argtypes = []
    user32.CloseClipboard.restype = ctypes.c_bool
    user32.GetClipboardData.argtypes = [ctypes.c_uint]
    user32.GetClipboardData.restype = ctypes.c_void_p
    user32.SetClipboardData.argtypes = [ctypes.c_uint, ctypes.c_void_p]
    user32.SetClipboardData.restype = ctypes.c_void_p
    user32.EmptyClipboard.argtypes = []
    user32.EmptyClipboard.restype = ctypes.c_bool
    kernel32.GlobalAlloc.argtypes = [ctypes.c_uint, ctypes.c_size_t]
    kernel32.GlobalAlloc.restype = ctypes.c_void_p
    kernel32.GlobalLock.argtypes = [ctypes.c_void_p]
    kernel32.GlobalLock.restype = ctypes.c_void_p
    kernel32.GlobalUnlock.argtypes = [ctypes.c_void_p]
    kernel32.GlobalUnlock.restype = ctypes.c_bool
    kernel32.GlobalFree.argtypes = [ctypes.c_void_p]
    kernel32.GlobalFree.restype = ctypes.c_void_p
    return user32, kernel32


def clipboard_read() -> dict:
    if os.name != "nt":
        raise RuntimeError("Clipboard helper currently supports Windows only")
    user32, kernel32 = _clipboard_api()
    if not user32.OpenClipboard(None):
        raise RuntimeError("Could not open clipboard")
    try:
        handle = user32.GetClipboardData(CF_UNICODETEXT)
        if not handle:
            return {"text": ""}
        ptr = kernel32.GlobalLock(handle)
        if not ptr:
            return {"text": ""}
        try:
            text = ctypes.wstring_at(ptr)
        finally:
            kernel32.GlobalUnlock(handle)
        return {"text": text}
    finally:
        user32.CloseClipboard()


def clipboard_write(text: str) -> dict:
    if os.name != "nt":
        raise RuntimeError("Clipboard helper currently supports Windows only")
    user32, kernel32 = _clipboard_api()
    payload = (str(text) + "\0").encode("utf-16-le")
    hglobal = kernel32.GlobalAlloc(GMEM_MOVEABLE, len(payload))
    if not hglobal:
        raise MemoryError("GlobalAlloc failed")
    locked = kernel32.GlobalLock(hglobal)
    if not locked:
        kernel32.GlobalFree(hglobal)
        raise MemoryError("GlobalLock failed")
    try:
        ctypes.memmove(locked, payload, len(payload))
    finally:
        kernel32.GlobalUnlock(hglobal)
    if not user32.OpenClipboard(None):
        kernel32.GlobalFree(hglobal)
        raise RuntimeError("Could not open clipboard")
    try:
        user32.EmptyClipboard()
        if not user32.SetClipboardData(CF_UNICODETEXT, hglobal):
            kernel32.GlobalFree(hglobal)
            raise RuntimeError("SetClipboardData failed")
        hglobal = None
    finally:
        user32.CloseClipboard()
    return {"ok": True, "chars": len(str(text))}


# ---------------------------------------------------------------------------
# Binary file transfer in bounded chunks
# ---------------------------------------------------------------------------

def binary_read(path: str, offset: int = 0, limit: int = 65536) -> dict:
    p = Path(path)
    if not p.is_file():
        raise FileNotFoundError(str(p))
    size = p.stat().st_size
    offset = max(0, int(offset))
    limit = min(max(1, int(limit)), 262144)
    with open(p, "rb") as f:
        f.seek(offset)
        data = f.read(limit)
    return {
        "path": str(p),
        "offset": offset,
        "bytes": len(data),
        "total_bytes": size,
        "next_offset": offset + len(data) if offset + len(data) < size else None,
        "base64": base64.b64encode(data).decode("ascii"),
        "sha256": _sha256(p) if size <= 50 * 1024 * 1024 else None,
    }


def binary_write(path: str, data_base64: str, offset: Optional[int] = None,
                 append: bool = False, truncate: bool = False, create_dirs: bool = True) -> dict:
    p = Path(path)
    if create_dirs:
        p.parent.mkdir(parents=True, exist_ok=True)
    data = base64.b64decode(data_base64.encode("ascii"), validate=True)

    if truncate:
        with open(p, "wb"):
            pass

    if append:
        mode = "ab"
    elif offset is None:
        mode = "wb"
    else:
        mode = "r+b" if p.exists() else "wb"

    with open(p, mode) as f:
        if offset is not None and not append:
            f.seek(max(0, int(offset)))
        f.write(data)

    size = p.stat().st_size
    return {
        "ok": True,
        "path": str(p),
        "written": len(data),
        "size": size,
        "sha256": _sha256(p) if size <= 50 * 1024 * 1024 else None,
    }


# ---------------------------------------------------------------------------
# Agent task state — orchestration only, no LLM
# ---------------------------------------------------------------------------

def _task_path(task_id: str) -> Path:
    return TASK_DIR / ("%s.json" % task_id)


def _load_task(task_id: str) -> dict:
    p = _task_path(task_id)
    if not p.exists():
        raise ValueError("Unknown task_id")
    return json.loads(p.read_text(encoding="utf-8"))


def _save_task(task: dict) -> dict:
    task["updated_at"] = _now()
    _task_path(task["task_id"]).write_text(json.dumps(task, ensure_ascii=False, indent=2), encoding="utf-8")
    return task


def task_start(goal: str, workspace: Optional[str] = None, plan=None,
               auto_tests: bool = True, auto_commit: bool = False) -> dict:
    task_id = str(uuid.uuid4())
    if plan is None:
        plan = []
    if isinstance(plan, str):
        plan = [plan]
    task = {
        "task_id": task_id,
        "goal": str(goal),
        "workspace": workspace,
        "status": "running",
        "plan": list(plan),
        "current_step": 0,
        "steps": [],
        "checkpoints": [],
        "auto_tests": bool(auto_tests),
        "auto_commit": bool(auto_commit),
        "created_at": _now(),
        "updated_at": _now(),
        "note": "ChatGPT remains the planner. Local Commander stores state and executes supplied steps.",
    }
    _save_task(task)
    return task


def task_status(task_id: Optional[str] = None) -> dict:
    if task_id:
        return _load_task(task_id)
    tasks = []
    for p in sorted(TASK_DIR.glob("*.json"), key=lambda x: x.stat().st_mtime, reverse=True)[:20]:
        try:
            t = json.loads(p.read_text(encoding="utf-8"))
            tasks.append({
                "task_id": t["task_id"],
                "goal": t["goal"],
                "status": t["status"],
                "current_step": t.get("current_step", 0),
                "updated_at": t.get("updated_at"),
            })
        except Exception:
            pass
    return {"tasks": tasks}


def task_step(task_id: str, title: str, action: Optional[str] = None,
              result: Optional[str] = None, status: str = "completed",
              validation: Optional[str] = None) -> dict:
    task = _load_task(task_id)
    if task["status"] in ("cancelled", "completed"):
        raise RuntimeError("Task is not active")
    step = {
        "index": len(task["steps"]) + 1,
        "title": str(title),
        "action": action,
        "result": result,
        "status": status,
        "validation": validation,
        "time": _now(),
    }
    task["steps"].append(step)
    task["current_step"] = step["index"]
    _save_task(task)
    return {"task_id": task_id, "step": step, "task_status": task["status"]}


def task_pause(task_id: str) -> dict:
    task = _load_task(task_id)
    task["status"] = "paused"
    return _save_task(task)


def task_resume(task_id: str) -> dict:
    task = _load_task(task_id)
    task["status"] = "running"
    return _save_task(task)


def task_cancel(task_id: str, reason: Optional[str] = None) -> dict:
    task = _load_task(task_id)
    task["status"] = "cancelled"
    task["cancel_reason"] = reason
    return _save_task(task)


def task_complete(task_id: str, summary: Optional[str] = None) -> dict:
    task = _load_task(task_id)
    task["status"] = "completed"
    task["summary"] = summary
    return _save_task(task)


def task_checkpoint(task_id: str, workspace: Optional[str] = None, label: Optional[str] = None) -> dict:
    task = _load_task(task_id)
    ws = Path(workspace or task.get("workspace") or Path.home())
    checkpoint_id = str(uuid.uuid4())
    cp = {
        "checkpoint_id": checkpoint_id,
        "label": label or ("checkpoint-%d" % (len(task["checkpoints"]) + 1)),
        "workspace": str(ws),
        "created_at": _now(),
        "git_head": None,
        "git_status": None,
        "git_diff": None,
    }

    git = shutil.which("git")
    if git and (ws / ".git").exists():
        def run(args):
            p = subprocess.run([git] + args, cwd=str(ws), stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True, errors="replace")
            return p.stdout
        cp["git_head"] = run(["rev-parse", "HEAD"]).strip()
        cp["git_status"] = run(["status", "--short"])
        cp["git_diff"] = run(["diff", "--binary"])[:200000]

    cp_path = CHECKPOINT_DIR / ("%s.json" % checkpoint_id)
    cp_path.write_text(json.dumps(cp, ensure_ascii=False, indent=2), encoding="utf-8")
    task["checkpoints"].append(cp)
    _save_task(task)
    return cp


def task_current_summary() -> dict:
    items = task_status().get("tasks", [])
    active = next((t for t in items if t["status"] in ("running", "paused")), None)
    return active or {}
