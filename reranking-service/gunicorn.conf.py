import os

bind = f"0.0.0.0:{os.getenv('PORT', '8001')}"
workers = int(os.getenv("WORKERS", "2"))
worker_class = "uvicorn.workers.UvicornWorker"
timeout = 120
keepalive = 5
loglevel = "info"
accesslog = "-"
errorlog = "-"
