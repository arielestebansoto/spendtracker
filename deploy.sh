#!/bin/bash
set -e

cd /home/ec2-user/dev/spendtracker

echo "Pulling main repository"
git pull origin main

echo "Clean cache images..."
docker builder prune -af
docker image prune -f

COMPOSE="-f docker-compose.yml -f docker-compose.prod.yml"

echo "Pulling latest images..."
docker compose $COMPOSE pull

echo "Deploying..."
docker compose $COMPOSE up -d --remove-orphans

echo "Cleaning old images..."
docker image prune -f

echo "Deploy finished!"
