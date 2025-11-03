#!/bin/bash

# Скрипт для быстрого деплоя на Яндекс Облако
# Использование: ./deploy.sh

# Цвета для вывода
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Конфигурация (измените на свои данные)
SERVER_USER="your-username"
SERVER_IP="your-server-ip"
SERVER_PATH="/var/www/defendend.dev"

echo -e "${BLUE}🔨 Building production...${NC}"
./gradlew jsBrowserProductionWebpack --console=plain

if [ $? -ne 0 ]; then
    echo "❌ Build failed!"
    exit 1
fi

echo -e "${BLUE}📤 Deploying to Yandex Cloud ($SERVER_IP)...${NC}"
scp -r build/dist/js/productionExecutable/* $SERVER_USER@$SERVER_IP:$SERVER_PATH/

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Done! Site updated at https://defendend.dev${NC}"
else
    echo "❌ Deploy failed!"
    exit 1
fi
