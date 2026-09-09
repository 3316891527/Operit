import { buildUrl, getUploadsBaseUrl, parseBool, repoPath, requestJson, requireToken, shellQuote } from './api';
import { getTerminalSession } from './local/terminal';

export type RepoIdParams = {
    owner: string;
    repo: string;
};

function compactAsset(item: any) {
    if (!item) return item;
    return {
        id: item.id,
        name: item.name,
        label: item.label,
        size: item.size,
        content_type: item.content_type,
        state: item.state,
        download_count: item.download_count,
        browser_download_url: item.browser_download_url,
        created_at: item.created_at,
        updated_at: item.updated_at,
        uploader: item.uploader?.login
    };
}

function compactRelease(item: any) {
    if (!item) return item;
    return {
        id: item.id,
        tag_name: item.tag_name,
        name: item.name,
        draft: item.draft,
        prerelease: item.prerelease,
        html_url: item.html_url,
        tarball_url: item.tarball_url,
        zipball_url: item.zipball_url,
        target_commitish: item.target_commitish,
        body: item.body,
        created_at: item.created_at,
        published_at: item.published_at,
        author: item.author?.login,
        assets: Array.isArray(item.assets) ? item.assets.map(compactAsset) : []
    };
}

export async function listReleases(params: RepoIdParams & { page?: number; per_page?: number }): Promise<any> {
    const url = buildUrl(repoPath(params.owner, params.repo, '/releases'), {
        page: params.page ?? 1,
        per_page: params.per_page ?? 30
    });
    const items = await requestJson<any[]>({ method: 'GET', url });
    return Array.isArray(items) ? items.map(compactRelease) : [];
}

export async function getRelease(params: RepoIdParams & { release_id: number | string }): Promise<any> {
    const url = buildUrl(repoPath(params.owner, params.repo, `/releases/${encodeURIComponent(String(params.release_id))}`));
    return compactRelease(await requestJson<any>({ method: 'GET', url }));
}

export async function getLatestRelease(params: RepoIdParams): Promise<any> {
    const url = buildUrl(repoPath(params.owner, params.repo, '/releases/latest'));
    return compactRelease(await requestJson<any>({ method: 'GET', url }));
}

export async function getReleaseByTag(params: RepoIdParams & { tag: string }): Promise<any> {
    const url = buildUrl(repoPath(params.owner, params.repo, `/releases/tags/${encodeURIComponent(params.tag)}`));
    return compactRelease(await requestJson<any>({ method: 'GET', url }));
}

export async function createRelease(
    params: RepoIdParams & {
        tag_name: string;
        target_commitish?: string;
        name?: string;
        body?: string;
        draft?: boolean | string;
        prerelease?: boolean | string;
        generate_release_notes?: boolean | string;
        make_latest?: string;
        discussion_category_name?: string;
    }
): Promise<any> {
    requireToken('create_release');
    const url = buildUrl(repoPath(params.owner, params.repo, '/releases'));
    return compactRelease(
        await requestJson<any>({
            method: 'POST',
            url,
            body: {
                tag_name: params.tag_name,
                target_commitish: params.target_commitish,
                name: params.name,
                body: params.body,
                draft: parseBool(params.draft),
                prerelease: parseBool(params.prerelease),
                generate_release_notes: parseBool(params.generate_release_notes),
                make_latest: params.make_latest,
                discussion_category_name: params.discussion_category_name
            }
        })
    );
}

export async function updateRelease(
    params: RepoIdParams & {
        release_id: number | string;
        tag_name?: string;
        target_commitish?: string;
        name?: string;
        body?: string;
        draft?: boolean | string;
        prerelease?: boolean | string;
        make_latest?: string;
        discussion_category_name?: string;
    }
): Promise<any> {
    requireToken('update_release');
    const url = buildUrl(repoPath(params.owner, params.repo, `/releases/${encodeURIComponent(String(params.release_id))}`));
    return compactRelease(
        await requestJson<any>({
            method: 'PATCH',
            url,
            body: {
                tag_name: params.tag_name,
                target_commitish: params.target_commitish,
                name: params.name,
                body: params.body,
                draft: parseBool(params.draft),
                prerelease: parseBool(params.prerelease),
                make_latest: params.make_latest,
                discussion_category_name: params.discussion_category_name
            }
        })
    );
}

export async function deleteRelease(params: RepoIdParams & { release_id: number | string }): Promise<any> {
    requireToken('delete_release');
    const url = buildUrl(repoPath(params.owner, params.repo, `/releases/${encodeURIComponent(String(params.release_id))}`));
    const result = await requestJson<any>({ method: 'DELETE', url });
    return { ok: true, release_id: params.release_id, result };
}

function guessFileName(path: string): string {
    const parts = String(path || '').replace(/\\/g, '/').split('/');
    return parts.filter(Boolean).pop() || 'asset.bin';
}

export async function uploadReleaseAsset(
    params: RepoIdParams & {
        release_id: number | string;
        asset_path: string;
        name?: string;
        label?: string;
        content_type?: string;
        environment?: 'android' | 'linux' | string;
    }
): Promise<any> {
    const token = requireToken('upload_release_asset');
    const name = params.name || guessFileName(params.asset_path);
    const contentType = params.content_type || 'application/octet-stream';
    const environment = (params.environment || 'android') as 'android' | 'linux';
    const sourcePath = String(params.asset_path || '').trim();
    if (!sourcePath) {
        throw new Error('asset_path is required');
    }

    const exists = await Tools.Files.exists(sourcePath, environment);
    if (!exists.exists) {
        throw new Error(`Asset file not found: ${sourcePath}`);
    }

    let linuxPath = sourcePath;
    if (environment !== 'linux') {
        linuxPath = `/tmp/operit-github-asset-${Date.now()}-${name}`;
        await Tools.Files.copy(sourcePath, linuxPath, false, environment, 'linux');
    }

    const uploadsPath = repoPath(
        params.owner,
        params.repo,
        `/releases/${encodeURIComponent(String(params.release_id))}/assets`
    );
    const qs: string[] = [`name=${encodeURIComponent(name)}`];
    if (params.label) qs.push(`label=${encodeURIComponent(params.label)}`);
    const finalUrl = `${getUploadsBaseUrl()}${uploadsPath}?${qs.join('&')}`;

    const headerFile = `/tmp/operit-github-upload-headers-${Date.now()}`;
    await Tools.Files.write(
        headerFile,
        [
            'header = "Accept: application/vnd.github+json"',
            `header = "Authorization: Bearer ${token}"`,
            'header = "X-GitHub-Api-Version: 2022-11-28"',
            `header = "Content-Type: ${contentType}"`,
            ''
        ].join('\n'),
        false,
        'linux'
    );

    const command = [
        'curl', '-sS', '-X', 'POST',
        '-K', shellQuote(headerFile),
        '--data-binary', '@' + shellQuote(linuxPath),
        '-w', shellQuote('\nHTTP_STATUS:%{http_code}'),
        shellQuote(finalUrl)
    ].join(' ');

    const sessionId = await getTerminalSession('github_tools_session');
    let result: any;
    try {
        result = await Tools.System.terminal.exec(sessionId, command, 120000);
    } finally {
        try {
            await Tools.Files.deleteFile(headerFile, false, 'linux');
        } catch (e) {
            // ignore cleanup errors
        }
    }
    const output = String(result.output || '');
    const statusMatch = output.match(/HTTP_STATUS:(\d+)\s*$/);
    const statusCode = statusMatch ? Number(statusMatch[1]) : result.exitCode === 0 ? 200 : 500;
    const bodyText = statusMatch ? output.slice(0, statusMatch.index).trim() : output.trim();

    if (statusCode < 200 || statusCode >= 300) {
        throw new Error(`GitHub API Error: ${statusCode}\n${bodyText.slice(0, 800)}`);
    }
    try {
        return compactAsset(JSON.parse(bodyText));
    } catch (e) {
        throw new Error(`Failed to parse upload response: ${bodyText.slice(0, 500)}`);
    }
}

export async function deleteReleaseAsset(params: RepoIdParams & { asset_id: number | string }): Promise<any> {
    requireToken('delete_release_asset');
    const url = buildUrl(repoPath(params.owner, params.repo, `/releases/assets/${encodeURIComponent(String(params.asset_id))}`));
    const result = await requestJson<any>({ method: 'DELETE', url });
    return { ok: true, asset_id: params.asset_id, result };
}
