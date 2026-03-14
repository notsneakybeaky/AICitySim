# Hyperinflation Monorepo Tasks

# List all available commands
default:
    just --list

# --- Setup ---
install-backend:
    cd netty && ./gradlew build

install-frontend:
    cd flutter && flutter pub get

install-ai:
    cd python && pip install -r requirements.txt

install-all: install-backend install-frontend install-ai

# --- Development ---
dev-backend:
    cd netty && ./gradlew run

dev-ai:
    cd python && uvicorn ai_service:app --reload --host 0.0.0.0 --port 9000

dev-frontend:
    cd flutter && flutter run -d chrome --web-port 3000

# Run backend and AI service concurrently
dev:
    just dev-backend & just dev-ai

# --- Docker ---
docker-ai:
    cd ai-service && docker build -t hyperinflation-ai . && docker run -it --rm --env-file .env -p 9000:9000 hyperinflation-ai

# --- Cleanup ---
clean-backend:
    cd backend && ./gradlew clean

clean-frontend:
    cd frontend && flutter clean

clean-all: clean-backend clean-frontend