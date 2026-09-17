import {afterEach, describe, expect, it, vi} from 'vitest';

const mocks = vi.hoisted(() => ({
    get: vi.fn(),
    storageState: vi.fn(),
    dispose: vi.fn()
}));

vi.mock('@playwright/test', () => ({
    request: {
        newContext: vi.fn(async () => ({
            get: mocks.get,
            storageState: mocks.storageState,
            dispose: mocks.dispose
        }))
    }
}));

const {default: stagingPreviewSetup} = await import('../../../../tests/e2e/staging-preview-setup.js');

afterEach(() => {
    delete process.env.E2E_PREVIEW_BOOTSTRAP_URL;
    delete process.env.E2E_PREVIEW_STORAGE_STATE;
    mocks.get.mockReset();
    mocks.storageState.mockReset();
    mocks.dispose.mockReset();
});

describe('staging preview setup', () => {
    it('requires both preview environment variables', async () => {
        await expect(stagingPreviewSetup()).rejects.toThrow('E2E_PREVIEW_BOOTSTRAP_URL');
    });

    it('rejects a bootstrap URL without the exact preview query', async () => {
        process.env.E2E_PREVIEW_BOOTSTRAP_URL = 'https://staging.bytedepth.cn/';
        process.env.E2E_PREVIEW_STORAGE_STATE = '/tmp/staging-preview.json';

        await expect(stagingPreviewSetup()).rejects.toThrow('must end with ?preview=true');
        expect(mocks.get).not.toHaveBeenCalled();
    });

    it('fails when the staging bootstrap request is not successful', async () => {
        process.env.E2E_PREVIEW_BOOTSTRAP_URL = 'https://staging.bytedepth.cn/?preview=true';
        process.env.E2E_PREVIEW_STORAGE_STATE = '/tmp/staging-preview.json';
        mocks.get.mockResolvedValue({ok: () => false, status: () => 302});

        await expect(stagingPreviewSetup()).rejects.toThrow('HTTP 302');
        expect(mocks.storageState).not.toHaveBeenCalled();
        expect(mocks.dispose).toHaveBeenCalledOnce();
    });

    it('stores the preview cookie state after a successful bootstrap', async () => {
        process.env.E2E_PREVIEW_BOOTSTRAP_URL = 'https://staging.bytedepth.cn/?preview=true';
        process.env.E2E_PREVIEW_STORAGE_STATE = '/tmp/staging-preview.json';
        mocks.get.mockResolvedValue({ok: () => true, status: () => 200});

        await stagingPreviewSetup();

        expect(mocks.get).toHaveBeenCalledWith('https://staging.bytedepth.cn/?preview=true', {failOnStatusCode: false});
        expect(mocks.storageState).toHaveBeenCalledWith({path: '/tmp/staging-preview.json'});
        expect(mocks.dispose).toHaveBeenCalledOnce();
    });
});
