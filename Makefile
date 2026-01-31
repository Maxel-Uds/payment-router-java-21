.PHONY: help

tag=latest
current_dir = $(shell pwd)

help: ## Lista de comandos
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

.DEFAULT_GOAL := help

build: ## Cria a imagem
	@./mvnw package -Dnative
	@docker build --no-cache -t quarkus-payment-router-java-21:test -f docker/Dockerfile.native-micro .

up: ## Sobe a infra completa
	@docker-compose -f infra-for-test/docker-compose.yml up

down: ## Para a aplicação
	@docker-compose -f infra-for-test/docker-compose.yml down

test: ## Executa o teste de estresse
	@k6 run -e MAX_REQUESTS=603 rinha-test/rinha.js