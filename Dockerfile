# Multi-stage build for minimal image size
# Stage 1: Builder - install dependencies
FROM python:3.12-slim AS builder

WORKDIR /app

# Install build dependencies for packages that need compilation (lxml)
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    libxml2-dev \
    libxslt-dev \
    && rm -rf /var/lib/apt/lists/*

# Create virtual environment and install dependencies
RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt


# Stage 2: Runtime - minimal production image
FROM python:3.12-slim AS runtime

WORKDIR /app

# Install runtime libraries for lxml
RUN apt-get update && apt-get install -y --no-install-recommends \
    libxml2 \
    libxslt1.1 \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN groupadd --gid 1000 appuser && \
    useradd --uid 1000 --gid appuser --shell /bin/bash --create-home appuser

# Copy virtual environment from builder
COPY --from=builder /opt/venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# Copy application files
COPY server.py .
COPY shared.py .
COPY routers/ ./routers/
COPY charset.json .
COPY font_decoder.py .
COPY static/ ./static/

# Set ownership to non-root user
RUN chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8199

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8199/api/health', timeout=5)" || exit 1

# Run uvicorn directly for proper signal handling
CMD ["uvicorn", "server:app", "--host", "0.0.0.0", "--port", "8199"]
