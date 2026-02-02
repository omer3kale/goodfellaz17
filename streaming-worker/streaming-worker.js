const puppeteer = require('puppeteer');
const express = require('express');
const app = express();
app.use(express.json());

const PORT = process.env.PORT || 3000;

async function streamTrack(trackUrl, proxy) {
    console.log(`Starting stream for ${trackUrl} via proxy ${proxy.host}:${proxy.port}`);

    const browser = await puppeteer.launch({
        headless: "new",
        args: [
            '--no-sandbox',
            '--disable-setuid-sandbox',
            `--proxy-server=${proxy.host}:${proxy.port}`,
            '--disable-blink-features=AutomationControlled'
        ]
    });

    const page = await browser.newPage();

    // Evasion: Randomize viewport
    await page.setViewport({
        width: 1920 + Math.floor(Math.random() * 100),
        height: 1080 + Math.floor(Math.random() * 100)
    });

    // Evasion: Random user agent
    const userAgents = [
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
    ];
    await page.setUserAgent(userAgents[Math.floor(Math.random() * userAgents.length)]);

    try {
        // Authenticate proxy if needed
        if (proxy.username && proxy.password) {
            await page.authenticate({ username: proxy.username, password: proxy.password });
        }

        await page.goto(trackUrl, { waitUntil: 'networkidle2', timeout: 60000 });

        // Evasion: Random stream duration (35-60s)
        const streamDuration = 35000 + Math.random() * 25000;
        console.log(`Streaming for ${Math.round(streamDuration/1000)}s...`);

        await page.evaluate((duration) => {
            return new Promise(resolve => setTimeout(resolve, duration));
        }, streamDuration);

        // Evasion: Random interactions (click seek bar)
        if (Math.random() > 0.7) {
            console.log("Simulating interaction...");
            // Simplified selector for demo
            try { await page.click('.playback-bar__progress-time'); } catch(e) {}
        }

        return {
            success: true,
            duration: Math.round(streamDuration / 1000),
            timestamp: new Date()
        };

    } catch (error) {
        console.error(`Stream failed: ${error.message}`);
        return { success: false, error: error.message };
    } finally {
        await browser.close();
    }
}

app.post('/stream', async (req, res) => {
    const { trackUrl, proxy } = req.body;
    if (!trackUrl || !proxy) {
        return res.status(400).json({ error: "Missing trackUrl or proxy" });
    }

    const result = await streamTrack(trackUrl, proxy);
    res.json(result);
});

app.listen(PORT, () => {
    console.log(`Streaming worker listening on port ${PORT}`);
});
