"""Мост между Android-приложением и ядром tg-ws-proxy (Flowseal, MIT)."""
import asyncio
import collections
import logging
import os
import threading

from proxy.config import proxy_config, parse_dc_ip_list
from proxy.tg_ws_proxy import _run
from proxy.utils import DomainCensorFilter

_thread = None
_loop = None
_stop_ev = None
_error = ""
_logs = collections.deque(maxlen=300)


class _RingHandler(logging.Handler):
    def emit(self, record):
        try:
            _logs.append(self.format(record))
        except Exception:
            pass


def _setup_logging():
    root = logging.getLogger()
    if any(isinstance(h, _RingHandler) for h in root.handlers):
        return
    h = _RingHandler()
    h.setFormatter(logging.Formatter("%(asctime)s  %(levelname)-5s  %(message)s", "%H:%M:%S"))
    h.addFilter(DomainCensorFilter())
    root.addHandler(h)
    root.setLevel(logging.INFO)
    for name in ("asyncio", "httpx", "httpcore", "hpack", "h2"):
        logging.getLogger(name).setLevel(logging.WARNING)


def _worker():
    global _loop, _stop_ev, _error
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    stop_ev = asyncio.Event()
    _loop, _stop_ev = loop, stop_ev
    try:
        loop.run_until_complete(_run(stop_event=stop_ev))
    except Exception as exc:
        _error = repr(exc)
        logging.getLogger("tg-mtproto-proxy").error("Proxy crashed: %s", _error)
    finally:
        try:
            pending = [t for t in asyncio.all_tasks(loop) if not t.done()]
            for t in pending:
                t.cancel()
            if pending:
                loop.run_until_complete(asyncio.gather(*pending, return_exceptions=True))
            loop.run_until_complete(loop.shutdown_asyncgens())
        finally:
            loop.close()
            _loop = _stop_ev = None


def new_secret():
    return os.urandom(16).hex()


def start(secret, port, dc_mode=None):
    """Запускает прокси в фоновом потоке. Возвращает True, если запущен."""
    global _thread, _error
    if _thread is not None and _thread.is_alive():
        return True
    _error = ""
    _setup_logging()
    pc = proxy_config
    pc.host = "127.0.0.1"
    pc.port = int(port)
    pc.secret = secret
    # Restore the original working route configuration (DC2 + DC4).
    # The DC4-only experiment caused connection failures on some networks.
    pc.dc_redirects = parse_dc_ip_list([
        "2:149.154.167.220",
        "4:149.154.167.220",
    ])
    _thread = threading.Thread(target=_worker, name="proxy", daemon=True)
    _thread.start()
    return True


def stop():
    global _thread
    if _loop is not None and _stop_ev is not None:
        _loop.call_soon_threadsafe(_stop_ev.set)
    if _thread is not None:
        _thread.join(timeout=5)
    _thread = None


def is_running():
    return _thread is not None and _thread.is_alive()


def last_error():
    return _error


def get_log():
    return "\n".join(_logs)


def get_stats():
    from proxy.stats import stats
    return stats.summary()
