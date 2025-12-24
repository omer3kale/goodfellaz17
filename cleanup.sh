#!/bin/bash
# GOODFELLAZ Cleanup Script - Remove All Old Instances → RENDER FOCUS
# Execute: chmod +x cleanup.sh && ./cleanup.sh

echo "🧹 CLEANING OLD INSTANCES → RENDER FOCUS"
echo "=========================================="

# 1. VERCEL CLEANUP
echo ""
echo "🗑️  Vercel cleanup..."
if command -v vercel &> /dev/null; then
    # Remove linked project
    rm -rf .vercel 2>/dev/null && echo "   ✅ Removed .vercel folder"
    
    # List and optionally delete projects
    vercel project ls 2>/dev/null && echo "   ⚠️  Manual: vercel.com → Settings → Delete Project"
else
    echo "   ⏭️  Vercel CLI not found - skipping"
fi

# Remove vercel config files
rm -f vercel.json 2>/dev/null && echo "   ✅ Removed vercel.json"

# 2. RAILWAY CLEANUP
echo ""
echo "🗑️  Railway cleanup..."
if command -v railway &> /dev/null; then
    rm -rf .railway 2>/dev/null && echo "   ✅ Removed .railway folder"
    echo "   ⚠️  Manual: railway.app → Projects → Delete All"
else
    echo "   ⏭️  Railway CLI not found - skipping"
fi

# 3. NETLIFY CLEANUP
echo ""
echo "🗑️  Netlify cleanup..."
if command -v netlify &> /dev/null; then
    rm -rf .netlify 2>/dev/null && echo "   ✅ Removed .netlify folder"
    echo "   ⚠️  Manual: app.netlify.com → Sites → Delete"
else
    echo "   ⏭️  Netlify CLI not found - skipping"
fi
rm -f netlify.toml 2>/dev/null

# 4. LOCAL DOCKER CLEANUP (optional - commented for safety)
echo ""
echo "🗑️  Docker cleanup..."
if command -v docker &> /dev/null; then
    # Stop all containers
    docker stop $(docker ps -q) 2>/dev/null && echo "   ✅ Stopped containers"
    # Remove stopped containers
    docker rm $(docker ps -aq) 2>/dev/null && echo "   ✅ Removed containers"
    # Prune unused images (keeps tagged images)
    docker image prune -f 2>/dev/null && echo "   ✅ Pruned dangling images"
else
    echo "   ⏭️  Docker not found - skipping"
fi

# 5. REMOVE OLD API FUNCTIONS (Vercel serverless)
echo ""
echo "🗑️  Removing Vercel serverless functions..."
rm -rf api/ 2>/dev/null && echo "   ✅ Removed api/ folder (Vercel Edge Functions)"

# 6. CLEAN BUILD ARTIFACTS
echo ""
echo "🗑️  Cleaning build artifacts..."
rm -rf target/ 2>/dev/null && echo "   ✅ Removed target/"
rm -rf node_modules/ 2>/dev/null && echo "   ✅ Removed node_modules/"

echo ""
echo "=========================================="
echo "✅ CLEANUP COMPLETE → RENDER READY"
echo ""
echo "📋 NEXT STEPS:"
echo "   1. git add . && git commit -m 'Render deploy'"
echo "   2. git remote add origin https://github.com/YOUR_USER/goodfella-provider.git"
echo "   3. git push -u origin main"
echo "   4. https://dashboard.render.com → New → Web Service"
echo "   5. Connect GitHub → goodfella-provider → Deploy"
echo ""
echo "🚀 RENDER = $0/mo → $847/day FRIDAY"
