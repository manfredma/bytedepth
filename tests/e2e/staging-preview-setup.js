import {request} from '@playwright/test';

export default async function stagingPreviewSetup() {
    const bootstrapUrl = process.env.E2E_PREVIEW_BOOTSTRAP_URL;
    const storageStatePath = process.env.E2E_PREVIEW_STORAGE_STATE;
    if (!bootstrapUrl || !storageStatePath) {
        throw new Error('Staging preview setup requires E2E_PREVIEW_BOOTSTRAP_URL and E2E_PREVIEW_STORAGE_STATE');
    }

    const parsed = new URL(bootstrapUrl);
    if (parsed.search !== '?preview=true') {
        throw new Error('Staging preview bootstrap URL must end with ?preview=true');
    }

    const context = await request.newContext();
    try {
        const response = await context.get(bootstrapUrl, {failOnStatusCode: false});
        if (!response.ok()) {
            throw new Error(`Staging preview bootstrap failed with HTTP ${response.status()}`);
        }
        await context.storageState({path: storageStatePath});
    } finally {
        await context.dispose();
    }
}
