.PHONY: help up infra down

infra: 
	@if command -v docker >/dev/null 2>&1; then docker compose -f ../docker-compose.yml up -d; else podman compose -f ../docker-compose.yml up -d; fi

up: 
	@./mvnw spring-boot:run

down: 
	@if command -v docker >/dev/null 2>&1; then docker compose -f ../docker-compose.yml down; else podman compose -f ../docker-compose.yml down; fi
