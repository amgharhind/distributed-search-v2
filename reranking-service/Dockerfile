FROM python:3.11-slim

WORKDIR /app

# Install dependencies first (layer-cached unless requirements.txt changes)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY app/ ./app/
COPY gunicorn.conf.py .

EXPOSE 8001

CMD ["gunicorn", "-c", "gunicorn.conf.py", "app.main:app"]
