#!/bin/bash

# Server Setup Script for AI Chat Backend
# Run this script on your Yandex Cloud VPS as root or with sudo

set -e

echo "🚀 Setting up AI Chat Backend on Yandex Cloud VPS"

# Update system
echo "📦 Updating system packages..."
apt-get update
apt-get upgrade -y

# Install PostgreSQL
echo "🗄️ Installing PostgreSQL..."
apt-get install -y postgresql postgresql-contrib

# Start and enable PostgreSQL
systemctl start postgresql
systemctl enable postgresql

# Create database and user
echo "🔧 Setting up database..."
sudo -u postgres psql <<EOF
CREATE DATABASE aichat;
CREATE USER aichat WITH ENCRYPTED PASSWORD 'change_this_password_123';
GRANT ALL PRIVILEGES ON DATABASE aichat TO aichat;
\q
EOF

echo "✅ PostgreSQL setup complete"

# Install Java 17 if not installed
echo "☕ Installing Java 17..."
apt-get install -y openjdk-17-jdk

# Create application user
echo "👤 Creating application user..."
if ! id -u aichat > /dev/null 2>&1; then
    useradd -r -s /bin/false aichat
fi

# Create application directory
echo "📁 Creating application directory..."
mkdir -p /opt/ai-chat
chown aichat:aichat /opt/ai-chat

# Create environment file
echo "🔐 Creating environment file..."
cat > /opt/ai-chat/.env <<EOF
DATABASE_URL=jdbc:postgresql://localhost:5432/aichat
DATABASE_USER=aichat
DATABASE_PASSWORD=change_this_password_123
CLAUDE_API_KEY=your_claude_api_key_here
DEEPSEEK_API_KEY=your_deepseek_api_key_here
JWT_SECRET=$(openssl rand -base64 32)
EOF

chmod 600 /opt/ai-chat/.env
chown aichat:aichat /opt/ai-chat/.env

echo "⚠️  IMPORTANT: Edit /opt/ai-chat/.env and add your API keys!"

# Create systemd service
echo "⚙️  Creating systemd service..."
cat > /etc/systemd/system/ai-chat.service <<'EOF'
[Unit]
Description=AI Chat Backend
After=network.target postgresql.service
Requires=postgresql.service

[Service]
Type=simple
User=aichat
Group=aichat
WorkingDirectory=/opt/ai-chat
EnvironmentFile=/opt/ai-chat/.env
ExecStart=/usr/bin/java -jar /opt/ai-chat/ai-chat-jvm-1.0.0.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=ai-chat

# Security hardening
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/opt/ai-chat

[Install]
WantedBy=multi-user.target
EOF

# Reload systemd
systemctl daemon-reload

echo "✅ Systemd service created"

# Configure sudo permissions for deployment
echo "🔑 Configuring sudo permissions..."
cat > /etc/sudoers.d/ai-chat-deploy <<EOF
defendend ALL=(ALL) NOPASSWD: /bin/mkdir, /bin/cp, /bin/chown, /bin/chmod, /bin/systemctl restart ai-chat, /bin/systemctl status ai-chat, /bin/systemctl is-active ai-chat
EOF

chmod 440 /etc/sudoers.d/ai-chat-deploy

echo "✅ Sudo permissions configured"

# Update Nginx configuration for backend proxy
echo "🌐 Updating Nginx configuration..."
if [ -f /etc/nginx/sites-available/defendend.dev ]; then
    # Backup existing config
    cp /etc/nginx/sites-available/defendend.dev /etc/nginx/sites-available/defendend.dev.backup

    # Add backend proxy location
    cat > /etc/nginx/sites-available/defendend.dev <<'NGINX_EOF'
server {
    listen 80;
    listen [::]:80;
    server_name defendend.dev www.defendend.dev;

    # Redirect HTTP to HTTPS
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name defendend.dev www.defendend.dev;

    # SSL certificates (Let's Encrypt)
    ssl_certificate /etc/letsencrypt/live/defendend.dev/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/defendend.dev/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;

    # Frontend
    location / {
        root /var/www/defendend.dev;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # Backend API
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;
    }

    # Health check
    location /health {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
    }

    # Logs
    access_log /var/log/nginx/defendend.dev.access.log;
    error_log /var/log/nginx/defendend.dev.error.log;
}
NGINX_EOF

    # Test and reload Nginx
    nginx -t && systemctl reload nginx
    echo "✅ Nginx configuration updated"
else
    echo "⚠️  Nginx config not found at /etc/nginx/sites-available/defendend.dev"
    echo "Please configure Nginx manually"
fi

echo ""
echo "========================================="
echo "✅ Server setup complete!"
echo "========================================="
echo ""
echo "Next steps:"
echo "1. Edit API keys in /opt/ai-chat/.env"
echo "   sudo nano /opt/ai-chat/.env"
echo ""
echo "2. Update database password"
echo "   sudo -u postgres psql"
echo "   ALTER USER aichat WITH PASSWORD 'your_new_password';"
echo ""
echo "3. Place your JAR file:"
echo "   sudo cp ai-chat-jvm-1.0.0.jar /opt/ai-chat/"
echo "   sudo chown aichat:aichat /opt/ai-chat/ai-chat-jvm-1.0.0.jar"
echo ""
echo "4. Start the service:"
echo "   sudo systemctl start ai-chat"
echo "   sudo systemctl enable ai-chat"
echo ""
echo "5. Check status:"
echo "   sudo systemctl status ai-chat"
echo "   sudo journalctl -u ai-chat -f"
echo ""
echo "Backend will be available at: https://defendend.dev/api/"
echo "========================================="
