export type GithubApiOptions = {
    baseUrl?: string;
    token?: string;
};

export type GithubHttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export type GithubRawResponse = {
    statusCode: number;
    statusMessage: string;
    headers: Record<string, string>;
    content: string;
};

export function getBaseUrl(): string {
    const fromEnv = (typeof getEnv === 'function' ? getEnv('GITHUB_API_BASE_URL') : undefined) || '';
    return (fromEnv && String(fromEnv).trim()) || 'https://api.github.com';
}

export function getToken(): string | undefined {
    const token = (typeof getEnv === 'function' ? getEnv('GITHUB_TOKEN') : undefined) || '';
    const trimmed = String(token || '').trim();
    return trimmed ? trimmed : undefined;
}

export function requireToken(operation: string): string {
    const token = getToken();
    if (!token) {
        throw new Error(`GITHUB_TOKEN is required for ${operation}.`);
    }
    return token;
}

export function buildUrl(pathname: string, query?: Record<string, string | number | boolean | undefined>): string {
    const base = getBaseUrl().replace(/\/+$/, '');
    const path = pathname.startsWith('/') ? pathname : `/${pathname}`;

    const qs: string[] = [];
    if (query) {
        Object.keys(query).forEach((k) => {
            const v = query[k];
            if (v === undefined || v === null || v === '') return;
            qs.push(`${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`);
        });
    }

    return qs.length > 0 ? `${base}${path}?${qs.join('&')}` : `${base}${path}`;
}

export function repoPath(owner: string, repo: string, suffix = ''): string {
    const extra = suffix ? (suffix.startsWith('/') ? suffix : `/${suffix}`) : '';
    return `/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}${extra}`;
}

export function encodeRef(ref: string): string {
    return String(ref || '')
        .split('/')
        .map((part) => encodeURIComponent(part))
        .join('/');
}

export function defaultHeaders(extra?: Record<string, string>): Record<string, string> {
    const headers: Record<string, string> = {
        Accept: 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28',
        'User-Agent': 'Operit-GitHub'
    };

    const token = getToken();
    if (token) {
        headers.Authorization = `Bearer ${token}`;
    }

    if (extra) {
        Object.keys(extra).forEach((k) => {
            headers[k] = extra[k];
        });
    }

    return headers;
}

export function createHttpClient(timeoutMs: number = 30000): OkHttpClient {
    return OkHttp.newBuilder()
        .connectTimeout(timeoutMs)
        .readTimeout(timeoutMs)
        .writeTimeout(timeoutMs)
        .followRedirects(true)
        .build();
}

export async function requestRaw(options: {
    method: GithubHttpMethod;
    url: string;
    headers?: Record<string, string>;
    body?: any;
    timeoutMs?: number;
}): Promise<GithubRawResponse> {
    const client = createHttpClient(options.timeoutMs ?? 30000);
    const req = client.newRequest().url(options.url).method(options.method);
    req.headers(defaultHeaders(options.headers));

    if (options.method !== 'GET') {
        const payload = options.body === undefined || options.body === null ? {} : options.body;
        req.body(JSON.stringify(payload), 'json');
    }

    const resp: OkHttpResponse = await req.build().execute();
    if (!resp.isSuccessful()) {
        throw new Error(`GitHub API Error: ${resp.statusCode} ${resp.statusMessage}\n${resp.content}`);
    }

    return {
        statusCode: resp.statusCode,
        statusMessage: resp.statusMessage,
        headers: resp.headers || {},
        content: typeof resp.content === 'string' ? resp.content : String(resp.content || '')
    };
}

export async function requestJson<T>(options: {
    method: GithubHttpMethod;
    url: string;
    headers?: Record<string, string>;
    body?: any;
    timeoutMs?: number;
}): Promise<T> {
    const resp = await requestRaw(options);
    const text = String(resp.content || '').trim();
    if (resp.statusCode === 204 || !text) {
        return { ok: true, statusCode: resp.statusCode } as T;
    }
    try {
        return JSON.parse(text) as T;
    } catch (e) {
        throw new Error(`Failed to parse GitHub JSON response (${resp.statusCode}): ${text.slice(0, 500)}`);
    }
}

export async function requestText(options: {
    method: GithubHttpMethod;
    url: string;
    headers?: Record<string, string>;
    body?: any;
    timeoutMs?: number;
}): Promise<{ text: string; statusCode: number; headers: Record<string, string> }> {
    const resp = await requestRaw(options);
    return {
        text: resp.content || '',
        statusCode: resp.statusCode,
        headers: resp.headers
    };
}

export function parseJsonParam<T = any>(value: any, fieldName: string): T | undefined {
    if (value === undefined || value === null || value === '') return undefined;
    if (typeof value === 'object') return value as T;
    if (typeof value !== 'string') return value as T;
    const trimmed = value.trim();
    if (!trimmed) return undefined;
    try {
        return JSON.parse(trimmed) as T;
    } catch (e) {
        throw new Error(`${fieldName} must be valid JSON.`);
    }
}

export function splitCsv(value?: string | string[]): string[] | undefined {
    if (value === undefined || value === null || value === '') return undefined;
    if (Array.isArray(value)) {
        const items = value.map((it) => String(it).trim()).filter(Boolean);
        return items.length > 0 ? items : undefined;
    }
    const items = String(value)
        .split(',')
        .map((it) => it.trim())
        .filter(Boolean);
    return items.length > 0 ? items : undefined;
}

export function compactCommit(commit: any) {
    if (!commit) return commit;
    return {
        sha: commit.sha || commit.commit?.tree?.sha,
        html_url: commit.html_url,
        message: commit.commit?.message || commit.message,
        author: commit.commit?.author?.name || commit.author?.login,
        date: commit.commit?.author?.date || commit.commit?.committer?.date,
        login: commit.author?.login
    };
}

export function compactFileChange(file: any, includePatch: boolean) {
    if (!file) return file;
    const item: any = {
        filename: file.filename,
        status: file.status,
        additions: file.additions,
        deletions: file.deletions,
        changes: file.changes,
        sha: file.sha,
        blob_url: file.blob_url,
        previous_filename: file.previous_filename
    };
    if (includePatch && typeof file.patch === 'string') {
        item.patch = file.patch;
    }
    return item;
}

export function truncateText(
    text: string,
    maxChars: number
): { text: string; truncated: boolean; original_length: number } {
    const src = String(text ?? '');
    const limit = maxChars > 0 ? maxChars : src.length;
    if (src.length <= limit) {
        return { text: src, truncated: false, original_length: src.length };
    }
    const headChars = Math.max(200, Math.floor(limit * 0.35));
    const tailChars = Math.max(200, limit - headChars - 80);
    const omitted = src.length - headChars - tailChars;
    return {
        text: `${src.slice(0, headChars)}\n\n... truncated ${omitted} chars ...\n\n${src.slice(-tailChars)}`,
        truncated: true,
        original_length: src.length
    };
}