/* METADATA
{
  "name": "github",
  "display_name": {
    "zh": "GitHub API",
    "en": "GitHub API"
  },
  "description": {
    "zh": "基于 GitHub REST API 的工具集合（不依赖 GitHub MCP）。包含 GitHub 侧（仓库/Issues/PR/文件提交/分支/差异/Actions/搜索/fork）与本地侧（apply_file 差异更新、terminal 终端）能力。",
    "en": "A toolkit built on the GitHub REST API (does not depend on GitHub MCP). Includes GitHub-side operations (repos/issues/PRs/commits/branches/diffs/Actions/search/fork) and local-side utilities (apply_file patch updates, terminal)."
  },
  "category": "Development",
  "env": [
    {
      "name": "GITHUB_TOKEN",
      "description": {
        "zh": "GitHub API 认证令牌",
        "en": "GitHub API authentication token"
      },
      "required": true
    },
    {
      "name": "GITHUB_API_BASE_URL",
      "description": {
        "zh": "GitHub API 基础 URL",
        "en": "GitHub API base URL"
      },
      "required": false,
      "defaultValue": "https://api.github.com"
    }
  ],
  "enabledByDefault": false,
  "tools": [
    {
      "name": "search_repositories",
      "description": {
        "zh": "搜索 GitHub 仓库（/search/repositories）。",
        "en": "Search GitHub repositories (/search/repositories)."
      },
      "parameters": [
        {
          "name": "query",
          "description": {
            "zh": "搜索关键词（GitHub search query）",
            "en": "Search keywords (GitHub search query)."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "sort",
          "description": {
            "zh": "排序字段：stars/forks/help-wanted-issues/updated",
            "en": "Sort field: stars/forks/help-wanted-issues/updated."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "order",
          "description": {
            "zh": "排序方向：desc/asc",
            "en": "Sort order: desc/asc."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "page",
          "description": {
            "zh": "页码（默认 1）",
            "en": "Page number (default: 1)."
          },
          "type": "number",
          "required": false
        },
        {
          "name": "per_page",
          "description": {
            "zh": "每页数量（默认 30，最大 100）",
            "en": "Items per page (default: 30, max: 100)."
          },
          "type": "number",
          "required": false
        }
      ]
    },
    {
      "name": "get_repository",
      "description": {
        "zh": "获取仓库信息（/repos/{owner}/{repo}）。",
        "en": "Get repository information (/repos/{owner}/{repo})."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        }
      ]
    },
    {
      "name": "list_issues",
      "description": {
        "zh": "列出仓库 Issues（/repos/{owner}/{repo}/issues）。",
        "en": "List repository issues (/repos/{owner}/{repo}/issues)."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "state",
          "description": {
            "zh": "open/closed/all（默认 open）",
            "en": "open/closed/all (default: open)."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "labels",
          "description": {
            "zh": "labels 逗号分隔",
            "en": "Labels, comma-separated."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "creator",
          "description": {
            "zh": "创建者 login",
            "en": "Creator login."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "page",
          "description": {
            "zh": "页码（默认 1）",
            "en": "Page number (default: 1)."
          },
          "type": "number",
          "required": false
        },
        {
          "name": "per_page",
          "description": {
            "zh": "每页数量（默认 30）",
            "en": "Items per page (default: 30)."
          },
          "type": "number",
          "required": false
        },
        {
          "name": "include_pull_requests",
          "description": {
            "zh": "是否保留 PR（默认 false）",
            "en": "Whether to include PRs (default: false)."
          },
          "type": "boolean",
          "required": false
        }
      ]
    },
    {
      "name": "get_issue",
      "description": {
        "zh": "获取单个 Issue 详情（/repos/{owner}/{repo}/issues/{issue_number}）。",
        "en": "Get a single issue."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "issue_number",
          "description": {
            "zh": "Issue 编号",
            "en": "Issue number."
          },
          "type": "number",
          "required": true
        }
      ]
    },
    {
      "name": "create_issue",
      "description": {
        "zh": "创建 Issue（/repos/{owner}/{repo}/issues）。",
        "en": "Create an issue."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "title",
          "description": {
            "zh": "Issue 标题",
            "en": "Issue title."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "body",
          "description": {
            "zh": "Issue 内容",
            "en": "Issue body."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "labels",
          "description": {
            "zh": "labels 数组",
            "en": "Labels array."
          },
          "type": "array",
          "required": false
        },
        {
          "name": "assignees",
          "description": {
            "zh": "assignees 数组",
            "en": "Assignees array."
          },
          "type": "array",
          "required": false
        }
      ]
    },
    {
      "name": "update_issue",
      "description": {
        "zh": "更新 Issue。",
        "en": "Update an issue."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "issue_number",
          "description": {
            "zh": "Issue 编号",
            "en": "Issue number."
          },
          "type": "number",
          "required": true
        },
        {
          "name": "title",
          "description": {
            "zh": "新标题",
            "en": "New title."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "body",
          "description": {
            "zh": "新内容",
            "en": "New body."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "state",
          "description": {
            "zh": "open/closed",
            "en": "open/closed."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "labels",
          "description": {
            "zh": "labels 数组",
            "en": "Labels array."
          },
          "type": "array",
          "required": false
        },
        {
          "name": "assignees",
          "description": {
            "zh": "assignees 数组",
            "en": "Assignees array."
          },
          "type": "array",
          "required": false
        }
      ]
    },
    {
      "name": "comment_issue",
      "description": {
        "zh": "给 Issue/PR 评论。",
        "en": "Comment on an issue/PR."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "issue_number",
          "description": {
            "zh": "Issue 编号",
            "en": "Issue number."
          },
          "type": "number",
          "required": true
        },
        {
          "name": "body",
          "description": {
            "zh": "评论内容",
            "en": "Comment body."
          },
          "type": "string",
          "required": true
        }
      ]
    },
    {
      "name": "list_issue_comments",
      "description": {
        "zh": "列出 Issue/PR 的评论。",
        "en": "List comments on an issue/PR."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "issue_number",
          "description": {
            "zh": "Issue 编号",
            "en": "Issue number."
          },
          "type": "number",
          "required": true
        },
        {
          "name": "page",
          "description": {
            "zh": "页码（默认 1）",
            "en": "Page number (default: 1)."
          },
          "type": "number",
          "required": false
        },
        {
          "name": "per_page",
          "description": {
            "zh": "每页数量（默认 30）",
            "en": "Items per page (default: 30)."
          },
          "type": "number",
          "required": false
        }
      ]
    },
    {
      "name": "list_pull_requests",
      "description": {
        "zh": "列出 PR（/repos/{owner}/{repo}/pulls）。",
        "en": "List pull requests."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "state",
          "description": {
            "zh": "open/closed/all（默认 open）",
            "en": "open/closed/all (default: open)."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "head",
          "description": {
            "zh": "head 分支过滤，格式 user:branch",
            "en": "Head branch filter (user:branch)."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "base",
          "description": {
            "zh": "base 分支过滤",
            "en": "Base branch filter."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "page",
          "description": {
            "zh": "页码（默认 1）",
            "en": "Page number (default: 1)."
          },
          "type": "number",
          "required": false
        },
        {
          "name": "per_page",
          "description": {
            "zh": "每页数量（默认 30）",
            "en": "Items per page (default: 30)."
          },
          "type": "number",
          "required": false
        }
      ]
    },
    {
      "name": "create_pull_request",
      "description": {
        "zh": "创建 PR。",
        "en": "Create a pull request."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "title",
          "description": {
            "zh": "PR 标题",
            "en": "PR title."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "head",
          "description": {
            "zh": "head 分支（含跨 fork 的 user:branch 格式）",
            "en": "Head branch (use user:branch for cross-fork)."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "base",
          "description": {
            "zh": "目标 base 分支",
            "en": "Target base branch."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "body",
          "description": {
            "zh": "PR 描述",
            "en": "PR body."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "draft",
          "description": {
            "zh": "是否草稿",
            "en": "Whether to create as draft."
          },
          "type": "boolean",
          "required": false
        }
      ]
    },
    {
      "name": "get_pull_request",
      "description": {
        "zh": "获取单个 PR 详情。",
        "en": "Get a single pull request."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "pull_number",
          "description": {
            "zh": "PR 编号",
            "en": "PR number."
          },
          "type": "number",
          "required": true
        }
      ]
    },
    {
      "name": "update_pull_request",
      "description": {
        "zh": "更新 PR 标题/描述/状态/base/draft。",
        "en": "Update PR title/body/state/base/draft."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "pull_number",
          "description": {
            "zh": "PR 编号",
            "en": "PR number."
          },
          "type": "number",
          "required": true
        },
        {
          "name": "title",
          "description": {
            "zh": "新标题",
            "en": "New title."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "body",
          "description": {
            "zh": "新描述",
            "en": "New body."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "state",
          "description": {
            "zh": "open/closed",
            "en": "open/closed."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "base",
          "description": {
            "zh": "新 base 分支",
            "en": "New base branch."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "draft",
          "description": {
            "zh": "是否草稿",
            "en": "Whether draft."
          },
          "type": "boolean",
          "required": false
        }
      ]
    },
    {
      "name": "merge_pull_request",
      "description": {
        "zh": "合并 PR。",
        "en": "Merge a pull request."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "pull_number",
          "description": {
            "zh": "PR 编号",
            "en": "PR number."
          },
          "type": "number",
          "required": true
        },
        {
          "name": "commit_title",
          "description": {
            "zh": "合并 commit 标题",
            "en": "Merge commit title."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "commit_message",
          "description": {
            "zh": "合并 commit 消息",
            "en": "Merge commit message."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "merge_method",
          "description": {
            "zh": "merge/squash/rebase（默认 merge）",
            "en": "merge/squash/rebase (default: merge)."
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "list_pull_request_files",
      "description": {
        "zh": "列出 PR 变更文件。",
        "en": "List files changed in a PR."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "pull_number",
          "description": {
            "zh": "PR 编号",
            "en": "PR number."
          },
          "type": "number",
          "required": true
        },
        {
          "name": "include_patch",
          "description": {
            "zh": "是否包含 patch 内容（默认 false）",
            "en": "Include patch content (default: false)."
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "page",
          "description": {
            "zh": "页码（默认 1）",
            "en": "Page number (default: 1)."
          },
          "type": "number",
          "required": false
        },
        {
          "name": "per_page",
          "description": {
            "zh": "每页数量（默认 100）",
            "en": "Items per page (default: 100)."
          },
          "type": "number",
          "required": false
        }
      ]
    },
    {
      "name": "get_pull_request_diff",
      "description": {
        "zh": "获取 PR 的 unified diff 文本。",
        "en": "Get PR unified diff text."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "pull_number",
          "description": {
            "zh": "PR 编号",
            "en": "PR number."
          },
          "type": "number",
          "required": true
        },
        {
          "name": "max_chars",
          "description": {
            "zh": "截断字符数（默认 20000）",
            "en": "Truncate to this many chars (default: 20000)."
          },
          "type": "number",
          "required": false
        }
      ]
    },
    {
      "name": "list_pull_request_commits",
      "description": {
        "zh": "列出 PR 的提交。",
        "en": "List commits in a PR."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "pull_number",
          "description": {
            "zh": "PR 编号",
            "en": "PR number."
          },
          "type": "number",
          "required": true
        },
        {
          "name": "page",
          "description": {
            "zh": "页码（默认 1）",
            "en": "Page number (default: 1)."
          },
          "type": "number",
          "required": false
        },
        {
          "name": "per_page",
          "description": {
            "zh": "每页数量（默认 30）",
            "en": "Items per page (default: 30)."
          },
          "type": "number",
          "required": false
        }
      ]
    },
    {
      "name": "list_pull_request_reviews",
      "description": {
        "zh": "列出 PR 的 review。",
        "en": "List reviews on a PR."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "pull_number",
          "description": {
            "zh": "PR 编号",
            "en": "PR number."
          },
          "type": "number",
          "required": true
        },
        {
          "name": "page",
          "description": {
            "zh": "页码（默认 1）",
            "en": "Page number (default: 1)."
          },
          "type": "number",
          "required": false
        },
        {
          "name": "per_page",
          "description": {
            "zh": "每页数量（默认 30）",
            "en": "Items per page (default: 30)."
          },
          "type": "number",
          "required": false
        }
      ]
    },
    {
      "name": "list_review_comments",
      "description": {
        "zh": "列出 PR 的行内 review comments。",
        "en": "List inline review comments on a PR."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "pull_number",
          "description": {
            "zh": "PR 编号",
            "en": "PR number."
          },
          "type": "number",
          "required": true
        },
        {
          "name": "page",
          "description": {
            "zh": "页码（默认 1）",
            "en": "Page number (default: 1)."
          },
          "type": "number",
          "required": false
        },
        {
          "name": "per_page",
          "description": {
            "zh": "每页数量（默认 30）",
            "en": "Items per page (default: 30)."
          },
          "type": "number",
          "required": false
        }
      ]
    },
    {
      "name": "create_review",
      "description": {
        "zh": "提交 PR review（APPROVE/REQUEST_CHANGES/COMMENT）。",
        "en": "Submit a PR review (APPROVE/REQUEST_CHANGES/COMMENT)."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "pull_number",
          "description": {
            "zh": "PR 编号",
            "en": "PR number."
          },
          "type": "number",
          "required": true
        },
        {
          "name": "body",
          "description": {
            "zh": "review 总评内容",
            "en": "Review body."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "event",
          "description": {
            "zh": "APPROVE/REQUEST_CHANGES/COMMENT（默认 COMMENT）",
            "en": "APPROVE/REQUEST_CHANGES/COMMENT (default: COMMENT)."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "commit_id",
          "description": {
            "zh": "关联的 commit sha",
            "en": "Commit SHA to associate."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "comments",
          "description": {
            "zh": "行内评论数组（JSON 字符串），每项含 path/line/body",
            "en": "Inline comments array (JSON string), each with path/line/body."
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "reply_review_comment",
      "description": {
        "zh": "回复 PR 行内 review comment。",
        "en": "Reply to a PR inline review comment."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "pull_number",
          "description": {
            "zh": "PR 编号",
            "en": "PR number."
          },
          "type": "number",
          "required": true
        },
        {
          "name": "comment_id",
          "description": {
            "zh": "被回复的 comment id",
            "en": "Comment ID to reply to."
          },
          "type": "number",
          "required": true
        },
        {
          "name": "body",
          "description": {
            "zh": "回复内容",
            "en": "Reply body."
          },
          "type": "string",
          "required": true
        }
      ]
    },
    {
      "name": "request_reviewers",
      "description": {
        "zh": "请求 PR reviewer。",
        "en": "Request reviewers for a PR."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "pull_number",
          "description": {
            "zh": "PR 编号",
            "en": "PR number."
          },
          "type": "number",
          "required": true
        },
        {
          "name": "reviewers",
          "description": {
            "zh": "reviewer login 列表，逗号分隔或数组",
            "en": "Reviewer logins, comma-separated or array."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "team_reviewers",
          "description": {
            "zh": "team reviewer slug 列表，逗号分隔或数组",
            "en": "Team reviewer slugs, comma-separated or array."
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "get_file_content",
      "description": {
        "zh": "获取仓库文件内容（/repos/{owner}/{repo}/contents/{path}）。",
        "en": "Get file content from a repository."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "path",
          "description": {
            "zh": "文件路径（相对仓库根目录）",
            "en": "File path relative to repository root."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "ref",
          "description": {
            "zh": "分支/tag/commit（默认默认分支）",
            "en": "Branch/tag/commit (default: default branch)."
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "create_or_update_file",
      "description": {
        "zh": "创建或更新仓库文件。更新时需传 sha。",
        "en": "Create or update a file in the repository. Provide sha when updating."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "path",
          "description": {
            "zh": "文件路径",
            "en": "File path."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "message",
          "description": {
            "zh": "commit 消息",
            "en": "Commit message."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "content",
          "description": {
            "zh": "文件内容（明文，会自动 base64 编码）",
            "en": "File content (plain text, will be base64-encoded)."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "sha",
          "description": {
            "zh": "更新时必填：现有文件的 blob sha",
            "en": "Required when updating: existing file blob SHA."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "branch",
          "description": {
            "zh": "目标分支（默认默认分支）",
            "en": "Target branch (default: default branch)."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "content_encoding",
          "description": {
            "zh": "内容编码：utf-8（默认）或 base64",
            "en": "Content encoding: utf-8 (default) or base64."
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "delete_file",
      "description": {
        "zh": "删除仓库文件。",
        "en": "Delete a file from the repository."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "path",
          "description": {
            "zh": "文件路径",
            "en": "File path."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "message",
          "description": {
            "zh": "commit 消息",
            "en": "Commit message."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "sha",
          "description": {
            "zh": "文件的 blob sha",
            "en": "File blob SHA."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "branch",
          "description": {
            "zh": "目标分支",
            "en": "Target branch."
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "create_branch",
      "description": {
        "zh": "创建分支（通过 /repos/{owner}/{repo}/git/refs）。",
        "en": "Create a branch (via /repos/{owner}/{repo}/git/refs)."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "new_branch",
          "description": {
            "zh": "新分支名",
            "en": "New branch name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "from_branch",
          "description": {
            "zh": "基于哪个分支创建（默认仓库默认分支）",
            "en": "Branch to create from (default: repository default branch)."
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "list_branches",
      "description": {
        "zh": "列出仓库分支。",
        "en": "List repository branches."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "protected_only",
          "description": {
            "zh": "只返回受保护分支（默认 false）",
            "en": "Return only protected branches (default: false)."
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "page",
          "description": {
            "zh": "页码（默认 1）",
            "en": "Page number (default: 1)."
          },
          "type": "number",
          "required": false
        },
        {
          "name": "per_page",
          "description": {
            "zh": "每页数量（默认 30）",
            "en": "Items per page (default: 30)."
          },
          "type": "number",
          "required": false
        }
      ]
    },
    {
      "name": "list_commits",
      "description": {
        "zh": "列出仓库提交。",
        "en": "List repository commits."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "sha",
          "description": {
            "zh": "分支/tag/commit sha（默认默认分支）",
            "en": "Branch/tag/commit SHA (default: default branch)."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "path",
          "description": {
            "zh": "只返回修改了该路径的提交",
            "en": "Only commits affecting this path."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "author",
          "description": {
            "zh": "作者 login 过滤",
            "en": "Author login filter."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "page",
          "description": {
            "zh": "页码（默认 1）",
            "en": "Page number (default: 1)."
          },
          "type": "number",
          "required": false
        },
        {
          "name": "per_page",
          "description": {
            "zh": "每页数量（默认 20）",
            "en": "Items per page (default: 20)."
          },
          "type": "number",
          "required": false
        }
      ]
    },
    {
      "name": "get_commit",
      "description": {
        "zh": "获取单个提交详情。",
        "en": "Get a single commit."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "ref",
          "description": {
            "zh": "commit sha / 分支 / tag",
            "en": "Commit SHA, branch, or tag."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "include_files",
          "description": {
            "zh": "是否包含文件变更列表（默认 true）",
            "en": "Whether to include file changes (default: true)."
          },
          "type": "boolean",
          "required": false
        }
      ]
    },
    {
      "name": "compare_refs",
      "description": {
        "zh": "比较两个 ref。",
        "en": "Compare two refs."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "base",
          "description": {
            "zh": "base ref（分支/tag/sha）",
            "en": "Base ref (branch/tag/sha)."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "head",
          "description": {
            "zh": "head ref（分支/tag/sha）",
            "en": "Head ref (branch/tag/sha)."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "include_files",
          "description": {
            "zh": "是否包含文件列表（默认 true）",
            "en": "Whether to include file list (default: true)."
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "include_patch",
          "description": {
            "zh": "文件列表是否包含 patch（默认 false）",
            "en": "Whether file list includes patch (default: false)."
          },
          "type": "boolean",
          "required": false
        }
      ]
    },
    {
      "name": "get_compare_diff",
      "description": {
        "zh": "获取两个 ref 之间的 unified diff 文本。",
        "en": "Get unified diff text between two refs."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "base",
          "description": {
            "zh": "base ref",
            "en": "Base ref."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "head",
          "description": {
            "zh": "head ref",
            "en": "Head ref."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "max_chars",
          "description": {
            "zh": "截断字符数（默认 20000）",
            "en": "Truncate to this many chars (default: 20000)."
          },
          "type": "number",
          "required": false
        }
      ]
    },
    {
      "name": "list_workflows",
      "description": {
        "zh": "列出仓库 GitHub Actions workflow。",
        "en": "List GitHub Actions workflows."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "page",
          "description": {
            "zh": "页码（默认 1）",
            "en": "Page number (default: 1)."
          },
          "type": "number",
          "required": false
        },
        {
          "name": "per_page",
          "description": {
            "zh": "每页数量（默认 30）",
            "en": "Items per page (default: 30)."
          },
          "type": "number",
          "required": false
        }
      ]
    },
    {
      "name": "list_workflow_runs",
      "description": {
        "zh": "列出 workflow run。",
        "en": "List workflow runs."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "workflow_id",
          "description": {
            "zh": "workflow id 或文件名（如 android-tests.yml），不传则列出所有",
            "en": "Workflow ID or filename (e.g. android-tests.yml); omit to list all."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "branch",
          "description": {
            "zh": "按分支过滤",
            "en": "Filter by branch."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "status",
          "description": {
            "zh": "按状态过滤：queued/in_progress/completed/failure/success 等",
            "en": "Filter by status: queued/in_progress/completed/failure/success etc."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "event",
          "description": {
            "zh": "按触发事件过滤：push/pull_request/workflow_dispatch 等",
            "en": "Filter by event: push/pull_request/workflow_dispatch etc."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "page",
          "description": {
            "zh": "页码（默认 1）",
            "en": "Page number (default: 1)."
          },
          "type": "number",
          "required": false
        },
        {
          "name": "per_page",
          "description": {
            "zh": "每页数量（默认 20）",
            "en": "Items per page (default: 20)."
          },
          "type": "number",
          "required": false
        }
      ]
    },
    {
      "name": "get_workflow_run",
      "description": {
        "zh": "获取单个 workflow run 详情。",
        "en": "Get a single workflow run."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "run_id",
          "description": {
            "zh": "workflow run id",
            "en": "Workflow run ID."
          },
          "type": "number",
          "required": true
        }
      ]
    },
    {
      "name": "trigger_workflow",
      "description": {
        "zh": "手动触发 workflow（workflow_dispatch）。",
        "en": "Manually trigger a workflow (workflow_dispatch)."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "workflow_id",
          "description": {
            "zh": "workflow id 或文件名（如 android-tests.yml）",
            "en": "Workflow ID or filename (e.g. android-tests.yml)."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "ref",
          "description": {
            "zh": "触发分支或 tag",
            "en": "Branch or tag to trigger on."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "inputs",
          "description": {
            "zh": "workflow inputs（JSON 字符串或对象）",
            "en": "Workflow inputs (JSON string or object)."
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "get_workflow_jobs",
      "description": {
        "zh": "获取 workflow run 的 jobs（含可选日志）。",
        "en": "Get jobs for a workflow run (with optional logs)."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "run_id",
          "description": {
            "zh": "workflow run id",
            "en": "Workflow run ID."
          },
          "type": "number",
          "required": true
        },
        {
          "name": "include_logs",
          "description": {
            "zh": "是否拉取每个 job 的日志（默认 false）",
            "en": "Whether to fetch logs for each job (default: false)."
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "failed_only",
          "description": {
            "zh": "只返回失败/非成功的 job（默认 false）",
            "en": "Return only failed/non-success jobs (default: false)."
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "max_log_chars",
          "description": {
            "zh": "日志截断字符数（默认 8000）",
            "en": "Log truncation in characters (default: 8000)."
          },
          "type": "number",
          "required": false
        },
        {
          "name": "page",
          "description": {
            "zh": "页码（默认 1）",
            "en": "Page number (default: 1)."
          },
          "type": "number",
          "required": false
        },
        {
          "name": "per_page",
          "description": {
            "zh": "每页数量（默认 50）",
            "en": "Items per page (default: 50)."
          },
          "type": "number",
          "required": false
        }
      ]
    },
    {
      "name": "rerun_workflow_run",
      "description": {
        "zh": "重新运行 workflow run（全部或仅失败 job）。",
        "en": "Re-run a workflow run (all jobs or failed jobs only)."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "run_id",
          "description": {
            "zh": "workflow run id",
            "en": "Workflow run ID."
          },
          "type": "number",
          "required": true
        },
        {
          "name": "failed_jobs_only",
          "description": {
            "zh": "只重跑失败 job（默认 false）",
            "en": "Only re-run failed jobs (default: false)."
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "enable_debug_logging",
          "description": {
            "zh": "是否开启 debug 日志",
            "en": "Whether to enable debug logging."
          },
          "type": "boolean",
          "required": false
        }
      ]
    },
    {
      "name": "cancel_workflow_run",
      "description": {
        "zh": "取消 workflow run。",
        "en": "Cancel a workflow run."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "run_id",
          "description": {
            "zh": "workflow run id",
            "en": "Workflow run ID."
          },
          "type": "number",
          "required": true
        }
      ]
    },
    {
      "name": "list_check_runs",
      "description": {
        "zh": "列出某个 commit 的 check runs。",
        "en": "List check runs for a commit."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "ref",
          "description": {
            "zh": "commit sha / 分支 / tag",
            "en": "Commit SHA, branch, or tag."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "status",
          "description": {
            "zh": "queued/in_progress/completed 过滤",
            "en": "Filter by status: queued/in_progress/completed."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "filter",
          "description": {
            "zh": "latest/all（默认 latest）",
            "en": "latest/all (default: latest)."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "page",
          "description": {
            "zh": "页码（默认 1）",
            "en": "Page number (default: 1)."
          },
          "type": "number",
          "required": false
        },
        {
          "name": "per_page",
          "description": {
            "zh": "每页数量（默认 30）",
            "en": "Items per page (default: 30)."
          },
          "type": "number",
          "required": false
        }
      ]
    },
    {
      "name": "search_code",
      "description": {
        "zh": "搜索代码（/search/code）。",
        "en": "Search code (/search/code)."
      },
      "parameters": [
        {
          "name": "query",
          "description": {
            "zh": "搜索查询（支持 GitHub code search 语法）",
            "en": "Search query (GitHub code search syntax, e.g. repo:owner/name keyword)."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "sort",
          "description": {
            "zh": "排序字段（indexed）",
            "en": "Sort field (indexed)."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "order",
          "description": {
            "zh": "排序方向：desc/asc",
            "en": "Sort order: desc/asc."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "page",
          "description": {
            "zh": "页码（默认 1）",
            "en": "Page number (default: 1)."
          },
          "type": "number",
          "required": false
        },
        {
          "name": "per_page",
          "description": {
            "zh": "每页数量（默认 20）",
            "en": "Items per page (default: 20)."
          },
          "type": "number",
          "required": false
        }
      ]
    },
    {
      "name": "search_issues",
      "description": {
        "zh": "搜索 Issue/PR（/search/issues）。",
        "en": "Search issues and PRs (/search/issues)."
      },
      "parameters": [
        {
          "name": "query",
          "description": {
            "zh": "搜索查询（支持 GitHub issue search 语法）",
            "en": "Search query (GitHub issue search syntax)."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "sort",
          "description": {
            "zh": "排序字段：comments/reactions/created/updated 等",
            "en": "Sort field: comments/reactions/created/updated etc."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "order",
          "description": {
            "zh": "排序方向：desc/asc",
            "en": "Sort order: desc/asc."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "page",
          "description": {
            "zh": "页码（默认 1）",
            "en": "Page number (default: 1)."
          },
          "type": "number",
          "required": false
        },
        {
          "name": "per_page",
          "description": {
            "zh": "每页数量（默认 20）",
            "en": "Items per page (default: 20)."
          },
          "type": "number",
          "required": false
        }
      ]
    },
    {
      "name": "get_authenticated_user",
      "description": {
        "zh": "获取当前认证用户信息（/user）。",
        "en": "Get the currently authenticated user (/user)."
      },
      "parameters": []
    },
    {
      "name": "get_rate_limit",
      "description": {
        "zh": "查询 GitHub API 请求频率限制（/rate_limit）。",
        "en": "Query GitHub API rate limit (/rate_limit)."
      },
      "parameters": []
    },
    {
      "name": "fork_repository",
      "description": {
        "zh": "fork 仓库（/repos/{owner}/{repo}/forks）。",
        "en": "Fork a repository."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "organization",
          "description": {
            "zh": "fork 到指定组织（不填则 fork 到个人账号）",
            "en": "Fork to a specific organization (omit for personal account)."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "name",
          "description": {
            "zh": "fork 后的仓库名（默认与原仓库同名）",
            "en": "Name for the forked repository (default: same as original)."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "default_branch_only",
          "description": {
            "zh": "只 fork 默认分支（默认 false）",
            "en": "Fork only the default branch (default: false)."
          },
          "type": "boolean",
          "required": false
        }
      ]
    },
    {
      "name": "sync_fork",
      "description": {
        "zh": "同步 fork 到上游（/repos/{owner}/{repo}/merge-upstream）。",
        "en": "Sync a fork with upstream."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "fork 仓库的 owner",
            "en": "Fork repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "fork 仓库名",
            "en": "Fork repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "branch",
          "description": {
            "zh": "要同步的分支（默认默认分支）",
            "en": "Branch to sync (default: default branch)."
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "patch_file_in_repo",
      "description": {
        "zh": "在仓库中对文件应用差异块 patch（先读取文件，按 [START-REPLACE]/[START-DELETE] 块替换内容后写回）。",
        "en": "Apply a block-based patch to a repository file (read -> apply [START-REPLACE]/[START-DELETE] blocks -> write back)."
      },
      "parameters": [
        {
          "name": "owner",
          "description": {
            "zh": "仓库 owner",
            "en": "Repository owner."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "repo",
          "description": {
            "zh": "仓库名",
            "en": "Repository name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "path",
          "description": {
            "zh": "文件路径",
            "en": "File path."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "message",
          "description": {
            "zh": "commit 消息",
            "en": "Commit message."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "patch",
          "description": {
            "zh": "差异块文本。格式：[START-REPLACE]\\n[OLD]\\n原内容\\n[/OLD]\\n[NEW]\\n新内容\\n[/NEW]\\n[END-REPLACE]，删除用 [START-DELETE] 配 [OLD] 块，可包含多个块。",
            "en": "Patch blocks. Format: [START-REPLACE]\\n[OLD]\\nold\\n[/OLD]\\n[NEW]\\nnew\\n[/NEW]\\n[END-REPLACE]; use [START-DELETE] with an [OLD] block to delete. Multiple blocks allowed."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "branch",
          "description": {
            "zh": "目标分支",
            "en": "Target branch."
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "apply_local_replace",
      "description": {
        "zh": "在本地文件中把 old 片段替换为 new 片段（不经过 GitHub API）。",
        "en": "Replace an old snippet with a new snippet in a local file (no GitHub API)."
      },
      "parameters": [
        {
          "name": "path",
          "description": {
            "zh": "本地文件路径",
            "en": "Local file path."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "old",
          "description": {
            "zh": "要被替换的原内容片段（精确匹配）",
            "en": "Existing snippet to replace (exact match)."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "new",
          "description": {
            "zh": "替换后的新内容片段",
            "en": "Replacement snippet."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "environment",
          "description": {
            "zh": "执行环境：android（默认）或 linux",
            "en": "Environment: android (default) or linux."
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "apply_local_delete",
      "description": {
        "zh": "在本地文件中删除指定内容片段（不经过 GitHub API）。",
        "en": "Delete a snippet from a local file (no GitHub API)."
      },
      "parameters": [
        {
          "name": "path",
          "description": {
            "zh": "本地文件路径",
            "en": "Local file path."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "old",
          "description": {
            "zh": "要删除的内容片段（精确匹配）",
            "en": "Snippet to delete (exact match)."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "environment",
          "description": {
            "zh": "执行环境：android（默认）或 linux",
            "en": "Environment: android (default) or linux."
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "overwrite_local_file",
      "description": {
        "zh": "用新内容覆盖写入本地文件（不经过 GitHub API）。",
        "en": "Overwrite a local file with new content (no GitHub API)."
      },
      "parameters": [
        {
          "name": "path",
          "description": {
            "zh": "本地文件路径",
            "en": "Local file path."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "content",
          "description": {
            "zh": "写入的完整内容",
            "en": "Full content to write."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "environment",
          "description": {
            "zh": "执行环境：android（默认）或 linux",
            "en": "Environment: android (default) or linux."
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "terminal_exec",
      "description": {
        "zh": "在 Operit 内置终端中执行命令（会话在多次调用间复用）。",
        "en": "Execute a command in the Operit built-in terminal (session is reused across calls)."
      },
      "parameters": [
        {
          "name": "command",
          "description": {
            "zh": "要执行的命令",
            "en": "Command to execute."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "session_name",
          "description": {
            "zh": "终端会话名（默认 github_tools_session）",
            "en": "Terminal session name (default: github_tools_session)."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "close",
          "description": {
            "zh": "执行后是否关闭会话（默认 false）",
            "en": "Whether to close the session after execution (default: false)."
          },
          "type": "boolean",
          "required": false
        }
      ]
    }
  ]
}
*/

import { listIssues, getIssue, createIssue, updateIssue, commentIssue, listIssueComments } from './github/issues';
import { listPullRequests, createPullRequest, getPullRequest, updatePullRequest, mergePullRequest, listPullRequestFiles, getPullRequestDiff, listPullRequestCommits, listPullRequestReviews, listReviewComments, createReview, replyReviewComment, requestReviewers } from './github/pulls';
import { getFileContent, createOrUpdateFile, deleteFile } from './github/contents';
import { createBranch } from './github/branches';
import { listBranches, listCommits, getCommit, compareRefs, getCompareDiff } from './github/git';
import { listWorkflows, listWorkflowRuns, getWorkflowRun, triggerWorkflow, getWorkflowJobs, rerunWorkflowRun, cancelWorkflowRun, listCheckRuns } from './github/actions';
import { searchRepositories, getRepository } from './github/repos';
import { searchCode, searchIssues } from './github/search';
import { getAuthenticatedUser, getRateLimit } from './github/users';
import { forkRepository, syncFork } from './github/forks';
import { patchFileInRepo } from './github/patch';
import { applyLocalReplace, applyLocalDelete, overwriteLocalFile } from './github/local/fileApply';
import { terminalExec } from './github/local/terminal';
import { wrap } from './utils/wrap';

export const toolImpl: Record<string, (params: any) => Promise<any>> = {
    search_repositories: (p) => wrap(searchRepositories as any, p, '搜索仓库成功', '搜索仓库失败'),
    get_repository: (p) => wrap(getRepository as any, p, '获取仓库成功', '获取仓库失败'),
    list_issues: (p) => wrap(listIssues as any, p, '列出 Issues 成功', '列出 Issues 失败'),
    get_issue: (p) => wrap(getIssue as any, p, '获取 Issue 成功', '获取 Issue 失败'),
    create_issue: (p) => wrap(createIssue as any, p, '创建 Issue 成功', '创建 Issue 失败'),
    update_issue: (p) => wrap(updateIssue as any, p, '更新 Issue 成功', '更新 Issue 失败'),
    comment_issue: (p) => wrap(commentIssue as any, p, '评论成功', '评论失败'),
    list_issue_comments: (p) => wrap(listIssueComments as any, p, '列出评论成功', '列出评论失败'),
    list_pull_requests: (p) => wrap(listPullRequests as any, p, '列出 PR 成功', '列出 PR 失败'),
    create_pull_request: (p) => wrap(createPullRequest as any, p, '创建 PR 成功', '创建 PR 失败'),
    get_pull_request: (p) => wrap(getPullRequest as any, p, '获取 PR 成功', '获取 PR 失败'),
    update_pull_request: (p) => wrap(updatePullRequest as any, p, '更新 PR 成功', '更新 PR 失败'),
    merge_pull_request: (p) => wrap(mergePullRequest as any, p, '合并 PR 成功', '合并 PR 失败'),
    list_pull_request_files: (p) => wrap(listPullRequestFiles as any, p, '获取 PR 文件成功', '获取 PR 文件失败'),
    get_pull_request_diff: (p) => wrap(getPullRequestDiff as any, p, '获取 PR diff 成功', '获取 PR diff 失败'),
    list_pull_request_commits: (p) => wrap(listPullRequestCommits as any, p, '获取 PR 提交成功', '获取 PR 提交失败'),
    list_pull_request_reviews: (p) => wrap(listPullRequestReviews as any, p, '获取 PR review 成功', '获取 PR review 失败'),
    list_review_comments: (p) => wrap(listReviewComments as any, p, '获取行内评论成功', '获取行内评论失败'),
    create_review: (p) => wrap(createReview as any, p, '提交 review 成功', '提交 review 失败'),
    reply_review_comment: (p) => wrap(replyReviewComment as any, p, '回复评论成功', '回复评论失败'),
    request_reviewers: (p) => wrap(requestReviewers as any, p, '请求 reviewer 成功', '请求 reviewer 失败'),
    get_file_content: (p) => wrap(getFileContent as any, p, '获取文件成功', '获取文件失败'),
    create_or_update_file: (p) => wrap(createOrUpdateFile as any, p, '写入文件成功', '写入文件失败'),
    delete_file: (p) => wrap(deleteFile as any, p, '删除文件成功', '删除文件失败'),
    create_branch: (p) => wrap(createBranch as any, p, '创建分支成功', '创建分支失败'),
    list_branches: (p) => wrap(listBranches as any, p, '列出分支成功', '列出分支失败'),
    list_commits: (p) => wrap(listCommits as any, p, '列出提交成功', '列出提交失败'),
    get_commit: (p) => wrap(getCommit as any, p, '获取提交成功', '获取提交失败'),
    compare_refs: (p) => wrap(compareRefs as any, p, '比较 refs 成功', '比较 refs 失败'),
    get_compare_diff: (p) => wrap(getCompareDiff as any, p, '获取 diff 成功', '获取 diff 失败'),
    list_workflows: (p) => wrap(listWorkflows as any, p, '列出 workflow 成功', '列出 workflow 失败'),
    list_workflow_runs: (p) => wrap(listWorkflowRuns as any, p, '获取 workflow run 成功', '获取 workflow run 失败'),
    get_workflow_run: (p) => wrap(getWorkflowRun as any, p, '获取 workflow run 成功', '获取 workflow run 失败'),
    trigger_workflow: (p) => wrap(triggerWorkflow as any, p, '触发 workflow 成功', '触发 workflow 失败'),
    get_workflow_jobs: (p) => wrap(getWorkflowJobs as any, p, '获取 workflow jobs 成功', '获取 workflow jobs 失败'),
    rerun_workflow_run: (p) => wrap(rerunWorkflowRun as any, p, '重新运行 workflow 成功', '重新运行 workflow 失败'),
    cancel_workflow_run: (p) => wrap(cancelWorkflowRun as any, p, '取消 workflow 成功', '取消 workflow 失败'),
    list_check_runs: (p) => wrap(listCheckRuns as any, p, '列出 check runs 成功', '列出 check runs 失败'),
    search_code: (p) => wrap(searchCode as any, p, '搜索代码成功', '搜索代码失败'),
    search_issues: (p) => wrap(searchIssues as any, p, '搜索 Issues 成功', '搜索 Issues 失败'),
    get_authenticated_user: (p) => wrap(getAuthenticatedUser as any, p, '获取用户成功', '获取用户失败'),
    get_rate_limit: (p) => wrap(getRateLimit as any, p, '获取 rate limit 成功', '获取 rate limit 失败'),
    fork_repository: (p) => wrap(forkRepository as any, p, 'fork 仓库成功', 'fork 仓库失败'),
    sync_fork: (p) => wrap(syncFork as any, p, '同步 fork 成功', '同步 fork 失败'),
    patch_file_in_repo: (p) => wrap(patchFileInRepo as any, p, 'patch 文件成功', 'patch 文件失败'),
    apply_local_replace: (p) => wrap(applyLocalReplace as any, p, '本地替换成功', '本地替换失败'),
    apply_local_delete: (p) => wrap(applyLocalDelete as any, p, '本地删除成功', '本地删除失败'),
    overwrite_local_file: (p) => wrap(overwriteLocalFile as any, p, '覆盖文件成功', '覆盖文件失败'),
    terminal_exec: (p) => wrap(terminalExec as any, p, '终端执行成功', '终端执行失败')
};

export async function main(params: any): Promise<any> {
    const tool = params?.tool as string | undefined;
    if (!tool) {
        return { ok: true, message: 'GitHub API package loaded', tools: Object.keys(toolImpl) };
    }
    const fn = toolImpl[tool];
    if (!fn) {
        return { success: false, message: `Unknown tool: ${tool}`, available: Object.keys(toolImpl) };
    }
    return fn(params);
}

export const search_repositories = toolImpl.search_repositories;
export const get_repository = toolImpl.get_repository;
export const list_issues = toolImpl.list_issues;
export const get_issue = toolImpl.get_issue;
export const create_issue = toolImpl.create_issue;
export const update_issue = toolImpl.update_issue;
export const comment_issue = toolImpl.comment_issue;
export const list_issue_comments = toolImpl.list_issue_comments;
export const list_pull_requests = toolImpl.list_pull_requests;
export const create_pull_request = toolImpl.create_pull_request;
export const get_pull_request = toolImpl.get_pull_request;
export const update_pull_request = toolImpl.update_pull_request;
export const merge_pull_request = toolImpl.merge_pull_request;
export const list_pull_request_files = toolImpl.list_pull_request_files;
export const get_pull_request_diff = toolImpl.get_pull_request_diff;
export const list_pull_request_commits = toolImpl.list_pull_request_commits;
export const list_pull_request_reviews = toolImpl.list_pull_request_reviews;
export const list_review_comments = toolImpl.list_review_comments;
export const create_review = toolImpl.create_review;
export const reply_review_comment = toolImpl.reply_review_comment;
export const request_reviewers = toolImpl.request_reviewers;
export const get_file_content = toolImpl.get_file_content;
export const create_or_update_file = toolImpl.create_or_update_file;
export const delete_file = toolImpl.delete_file;
export const create_branch = toolImpl.create_branch;
export const list_branches = toolImpl.list_branches;
export const list_commits = toolImpl.list_commits;
export const get_commit = toolImpl.get_commit;
export const compare_refs = toolImpl.compare_refs;
export const get_compare_diff = toolImpl.get_compare_diff;
export const list_workflows = toolImpl.list_workflows;
export const list_workflow_runs = toolImpl.list_workflow_runs;
export const get_workflow_run = toolImpl.get_workflow_run;
export const trigger_workflow = toolImpl.trigger_workflow;
export const get_workflow_jobs = toolImpl.get_workflow_jobs;
export const rerun_workflow_run = toolImpl.rerun_workflow_run;
export const cancel_workflow_run = toolImpl.cancel_workflow_run;
export const list_check_runs = toolImpl.list_check_runs;
export const search_code = toolImpl.search_code;
export const search_issues = toolImpl.search_issues;
export const get_authenticated_user = toolImpl.get_authenticated_user;
export const get_rate_limit = toolImpl.get_rate_limit;
export const fork_repository = toolImpl.fork_repository;
export const sync_fork = toolImpl.sync_fork;
export const patch_file_in_repo = toolImpl.patch_file_in_repo;
export const apply_local_replace = toolImpl.apply_local_replace;
export const apply_local_delete = toolImpl.apply_local_delete;
export const overwrite_local_file = toolImpl.overwrite_local_file;
export const terminal_exec = toolImpl.terminal_exec;
