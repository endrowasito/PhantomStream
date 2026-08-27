FROM python:3.10-slim
WORKDIR /app
RUN pip install websockets flask
COPY . .
CMD ["python", "server.py"]
