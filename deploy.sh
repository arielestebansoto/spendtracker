#!/bin/bash
set -e

cd /home/ec2-user/dev/spendtracker

echo "Clean cache images..."
docker builder prune -af
docker image prune -f

echo "Pulling latest images..."
docker compose pull

echo "Deploying..."
docker compose up -d --remove-orphans

echo "Cleaning old images..."
docker image prune -f

echo "Deploy finished!"
