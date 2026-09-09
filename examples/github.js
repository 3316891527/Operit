/* METADATA
{
  "name": "github",
  "display_name": {
    "zh": "GitHub API",
    "en": "GitHub API"
  },
  "description": {
    "zh": "基于 GitHub REST API 的工具集合（不依赖 GitHub MCP）。包含 GitHub 侧（仓库/Issues/PR/文件提交/分支/差异/Actions/Releases/Webhooks/协作者/Labels/Milestones/分支保护/统计/搜索/fork）与本地侧（apply_file 差异更新、terminal 终端）能力。",
    "en": "A toolkit built on the GitHub REST API (does not depend on GitHub MCP). Includes GitHub-side operations (repos/issues/PRs/commits/branches/diffs/Actions/releases/webhooks/collaborators/labels/milestones/branch protection/stats/search/fork) and local-side utilities (apply_file patch updates, terminal)."
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
    },
    {
      "name": "GITHUB_UPLOADS_BASE_URL",
      "description": {
        "zh": "GitHub Release 资产上传基础 URL（默认 https://uploads.github.com）",
        "en": "GitHub release asset upload base URL (default: https://uploads.github.com)."
      },
      "required": false,
      "defaultValue": "https://uploads.github.com"
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
        "zh": "创建或恢复分支（POST /repos/{owner}/{repo}/git/refs）。可从已有分支或指定 commit SHA 建 ref。",
        "en": "Create or restore a branch (POST /repos/{owner}/{repo}/git/refs). Point the new ref at an existing branch or a commit SHA."
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
            "zh": "基于哪个分支创建（默认仓库默认分支；若传了 from_sha 则忽略）",
            "en": "Branch to create from (default: repository default branch; ignored when from_sha is set)."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "from_sha",
          "description": {
            "zh": "基于哪个 commit SHA 创建（优先于 from_branch；用于从提交恢复已删分支）",
            "en": "Commit SHA to create from (takes precedence over from_branch; use this to restore a deleted branch)."
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "delete_branch",
      "description": {
        "zh": "删除非默认分支（DELETE /repos/{owner}/{repo}/git/refs/heads/{branch}）。不会删除仓库默认分支。",
        "en": "Delete a non-default branch (DELETE /repos/{owner}/{repo}/git/refs/heads/{branch}). Refuses to delete the repository default branch."
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
          "name": "branch",
          "description": {
            "zh": "要删除的分支名（不能是仓库默认分支）",
            "en": "Branch name to delete (cannot be the repository default branch)."
          },
          "type": "string",
          "required": true
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
    },
    {
      "name": "list_releases",
      "description": {
        "zh": "列出仓库 Releases。",
        "en": "List repository releases."
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
      "name": "get_release",
      "description": {
        "zh": "获取指定 Release。",
        "en": "Get a release."
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
          "name": "release_id",
          "description": {
            "zh": "Release ID",
            "en": "Release ID."
          },
          "type": "number",
          "required": true
        }
      ]
    },
    {
      "name": "get_latest_release",
      "description": {
        "zh": "获取最新的正式 Release。",
        "en": "Get the latest release."
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
      "name": "get_release_by_tag",
      "description": {
        "zh": "按 tag 获取 Release。",
        "en": "Get a release by tag."
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
          "name": "tag",
          "description": {
            "zh": "Git tag 名称",
            "en": "Git tag name."
          },
          "type": "string",
          "required": true
        }
      ]
    },
    {
      "name": "create_release",
      "description": {
        "zh": "创建 Release。",
        "en": "Create a release."
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
          "name": "tag_name",
          "description": {
            "zh": "tag 名称",
            "en": "Tag name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "target_commitish",
          "description": {
            "zh": "目标分支或 commit",
            "en": "Target branch or commit."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "name",
          "description": {
            "zh": "Release 标题",
            "en": "Release name."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "body",
          "description": {
            "zh": "Release 描述",
            "en": "Release body."
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
        },
        {
          "name": "prerelease",
          "description": {
            "zh": "是否预发布",
            "en": "Whether prerelease."
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "generate_release_notes",
          "description": {
            "zh": "是否自动生成说明",
            "en": "Whether to generate release notes."
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "make_latest",
          "description": {
            "zh": "latest 标记：true/false/legacy",
            "en": "Latest marker: true/false/legacy."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "discussion_category_name",
          "description": {
            "zh": "Discussion 分类名",
            "en": "Discussion category name."
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "update_release",
      "description": {
        "zh": "更新 Release。",
        "en": "Update a release."
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
          "name": "release_id",
          "description": {
            "zh": "Release ID",
            "en": "Release ID."
          },
          "type": "number",
          "required": true
        },
        {
          "name": "tag_name",
          "description": {
            "zh": "tag 名称",
            "en": "Tag name."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "target_commitish",
          "description": {
            "zh": "目标分支或 commit",
            "en": "Target branch or commit."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "name",
          "description": {
            "zh": "Release 标题",
            "en": "Release name."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "body",
          "description": {
            "zh": "Release 描述",
            "en": "Release body."
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
        },
        {
          "name": "prerelease",
          "description": {
            "zh": "是否预发布",
            "en": "Whether prerelease."
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "make_latest",
          "description": {
            "zh": "latest 标记：true/false/legacy",
            "en": "Latest marker: true/false/legacy."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "discussion_category_name",
          "description": {
            "zh": "Discussion 分类名",
            "en": "Discussion category name."
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "delete_release",
      "description": {
        "zh": "删除 Release。",
        "en": "Delete a release."
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
          "name": "release_id",
          "description": {
            "zh": "Release ID",
            "en": "Release ID."
          },
          "type": "number",
          "required": true
        }
      ]
    },
    {
      "name": "upload_release_asset",
      "description": {
        "zh": "向 Release 上传本地文件资产。",
        "en": "Upload a local file as a release asset."
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
          "name": "release_id",
          "description": {
            "zh": "Release ID",
            "en": "Release ID."
          },
          "type": "number",
          "required": true
        },
        {
          "name": "asset_path",
          "description": {
            "zh": "本地资产路径",
            "en": "Local asset path."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "name",
          "description": {
            "zh": "上传后的文件名（默认取路径文件名）",
            "en": "Uploaded filename (defaults to the path basename)."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "label",
          "description": {
            "zh": "资产说明",
            "en": "Asset label."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "content_type",
          "description": {
            "zh": "MIME 类型（默认 application/octet-stream）",
            "en": "MIME type (default application/octet-stream)."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "environment",
          "description": {
            "zh": "文件环境：android 或 linux",
            "en": "File environment: android or linux."
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "delete_release_asset",
      "description": {
        "zh": "删除 Release 资产。",
        "en": "Delete a release asset."
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
          "name": "asset_id",
          "description": {
            "zh": "资产 ID",
            "en": "Asset ID."
          },
          "type": "number",
          "required": true
        }
      ]
    },
    {
      "name": "list_webhooks",
      "description": {
        "zh": "列出仓库 Webhooks。",
        "en": "List repository webhooks."
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
      "name": "get_webhook",
      "description": {
        "zh": "获取仓库 Webhook。",
        "en": "Get a repository webhook."
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
          "name": "hook_id",
          "description": {
            "zh": "Webhook ID",
            "en": "Webhook ID."
          },
          "type": "number",
          "required": true
        }
      ]
    },
    {
      "name": "create_webhook",
      "description": {
        "zh": "创建仓库 Webhook。",
        "en": "Create a repository webhook."
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
          "name": "url",
          "description": {
            "zh": "Webhook 接收 URL",
            "en": "Webhook receiver URL."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "content_type",
          "description": {
            "zh": "内容类型：json 或 form",
            "en": "Content type: json or form."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "secret",
          "description": {
            "zh": "Webhook secret",
            "en": "Webhook secret."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "insecure_ssl",
          "description": {
            "zh": "是否允许不安全 SSL",
            "en": "Whether to allow insecure SSL."
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "events",
          "description": {
            "zh": "事件列表，逗号分隔或数组",
            "en": "Events, comma-separated or array."
          },
          "type": "array",
          "required": false
        },
        {
          "name": "active",
          "description": {
            "zh": "是否启用",
            "en": "Whether active."
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "config",
          "description": {
            "zh": "额外 config JSON",
            "en": "Additional config JSON."
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "update_webhook",
      "description": {
        "zh": "更新仓库 Webhook。",
        "en": "Update a repository webhook."
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
          "name": "hook_id",
          "description": {
            "zh": "Webhook ID",
            "en": "Webhook ID."
          },
          "type": "number",
          "required": true
        },
        {
          "name": "url",
          "description": {
            "zh": "Webhook 接收 URL",
            "en": "Webhook receiver URL."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "content_type",
          "description": {
            "zh": "内容类型：json 或 form",
            "en": "Content type: json or form."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "secret",
          "description": {
            "zh": "Webhook secret",
            "en": "Webhook secret."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "insecure_ssl",
          "description": {
            "zh": "是否允许不安全 SSL",
            "en": "Whether to allow insecure SSL."
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "events",
          "description": {
            "zh": "完整事件列表，逗号分隔或数组",
            "en": "Complete event list, comma-separated or array."
          },
          "type": "array",
          "required": false
        },
        {
          "name": "add_events",
          "description": {
            "zh": "追加事件列表",
            "en": "Events to add."
          },
          "type": "array",
          "required": false
        },
        {
          "name": "remove_events",
          "description": {
            "zh": "移除事件列表",
            "en": "Events to remove."
          },
          "type": "array",
          "required": false
        },
        {
          "name": "active",
          "description": {
            "zh": "是否启用",
            "en": "Whether active."
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "config",
          "description": {
            "zh": "额外 config JSON",
            "en": "Additional config JSON."
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "delete_webhook",
      "description": {
        "zh": "删除仓库 Webhook。",
        "en": "Delete a repository webhook."
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
          "name": "hook_id",
          "description": {
            "zh": "Webhook ID",
            "en": "Webhook ID."
          },
          "type": "number",
          "required": true
        }
      ]
    },
    {
      "name": "ping_webhook",
      "description": {
        "zh": "向仓库 Webhook 发送 ping。",
        "en": "Ping a repository webhook."
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
          "name": "hook_id",
          "description": {
            "zh": "Webhook ID",
            "en": "Webhook ID."
          },
          "type": "number",
          "required": true
        }
      ]
    },
    {
      "name": "list_collaborators",
      "description": {
        "zh": "列出仓库协作者。",
        "en": "List repository collaborators."
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
          "name": "affiliation",
          "description": {
            "zh": "筛选：outside/direct/all",
            "en": "Filter: outside/direct/all."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "permission",
          "description": {
            "zh": "权限筛选：pull/triage/push/maintain/admin",
            "en": "Permission filter: pull/triage/push/maintain/admin."
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
      "name": "check_collaborator",
      "description": {
        "zh": "检查用户是否为仓库协作者。",
        "en": "Check whether a user is a repository collaborator."
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
          "name": "username",
          "description": {
            "zh": "GitHub 用户名",
            "en": "GitHub username."
          },
          "type": "string",
          "required": true
        }
      ]
    },
    {
      "name": "add_collaborator",
      "description": {
        "zh": "添加仓库协作者。",
        "en": "Add a repository collaborator."
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
          "name": "username",
          "description": {
            "zh": "GitHub 用户名",
            "en": "GitHub username."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "permission",
          "description": {
            "zh": "权限：pull/triage/push/maintain/admin",
            "en": "Permission: pull/triage/push/maintain/admin."
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "remove_collaborator",
      "description": {
        "zh": "移除仓库协作者。",
        "en": "Remove a repository collaborator."
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
          "name": "username",
          "description": {
            "zh": "GitHub 用户名",
            "en": "GitHub username."
          },
          "type": "string",
          "required": true
        }
      ]
    },
    {
      "name": "get_collaborator_permission",
      "description": {
        "zh": "获取用户在仓库中的权限。",
        "en": "Get repository permissions for a user."
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
          "name": "username",
          "description": {
            "zh": "GitHub 用户名",
            "en": "GitHub username."
          },
          "type": "string",
          "required": true
        }
      ]
    },
    {
      "name": "list_labels",
      "description": {
        "zh": "列出仓库 Labels。",
        "en": "List repository labels."
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
      "name": "get_label",
      "description": {
        "zh": "获取仓库 Label。",
        "en": "Get a repository label."
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
          "name": "name",
          "description": {
            "zh": "Label 名称",
            "en": "Label name."
          },
          "type": "string",
          "required": true
        }
      ]
    },
    {
      "name": "create_label",
      "description": {
        "zh": "创建仓库 Label。",
        "en": "Create a repository label."
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
          "name": "name",
          "description": {
            "zh": "Label 名称",
            "en": "Label name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "color",
          "description": {
            "zh": "六位十六进制颜色，可带 #",
            "en": "Six-digit hexadecimal color, with optional #."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "description",
          "description": {
            "zh": "Label 描述",
            "en": "Label description."
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "update_label",
      "description": {
        "zh": "更新仓库 Label。",
        "en": "Update a repository label."
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
          "name": "name",
          "description": {
            "zh": "当前 Label 名称",
            "en": "Current label name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "new_name",
          "description": {
            "zh": "新 Label 名称",
            "en": "New label name."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "color",
          "description": {
            "zh": "六位十六进制颜色，可带 #",
            "en": "Six-digit hexadecimal color, with optional #."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "description",
          "description": {
            "zh": "Label 描述",
            "en": "Label description."
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "delete_label",
      "description": {
        "zh": "删除仓库 Label。",
        "en": "Delete a repository label."
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
          "name": "name",
          "description": {
            "zh": "Label 名称",
            "en": "Label name."
          },
          "type": "string",
          "required": true
        }
      ]
    },
    {
      "name": "list_milestones",
      "description": {
        "zh": "列出仓库 Milestones。",
        "en": "List repository milestones."
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
            "zh": "状态：open/closed/all",
            "en": "State: open/closed/all."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "sort",
          "description": {
            "zh": "排序：due_on/completeness",
            "en": "Sort: due_on/completeness."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "direction",
          "description": {
            "zh": "方向：asc/desc",
            "en": "Direction: asc/desc."
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
      "name": "get_milestone",
      "description": {
        "zh": "获取 Milestone。",
        "en": "Get a milestone."
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
          "name": "milestone_number",
          "description": {
            "zh": "Milestone 编号",
            "en": "Milestone number."
          },
          "type": "number",
          "required": true
        }
      ]
    },
    {
      "name": "create_milestone",
      "description": {
        "zh": "创建 Milestone。",
        "en": "Create a milestone."
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
            "zh": "Milestone 标题",
            "en": "Milestone title."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "state",
          "description": {
            "zh": "状态：open/closed",
            "en": "State: open/closed."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "description",
          "description": {
            "zh": "Milestone 描述",
            "en": "Milestone description."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "due_on",
          "description": {
            "zh": "截止时间，ISO 8601",
            "en": "Due date, ISO 8601."
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "update_milestone",
      "description": {
        "zh": "更新 Milestone。",
        "en": "Update a milestone."
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
          "name": "milestone_number",
          "description": {
            "zh": "Milestone 编号",
            "en": "Milestone number."
          },
          "type": "number",
          "required": true
        },
        {
          "name": "title",
          "description": {
            "zh": "Milestone 标题",
            "en": "Milestone title."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "state",
          "description": {
            "zh": "状态：open/closed",
            "en": "State: open/closed."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "description",
          "description": {
            "zh": "Milestone 描述",
            "en": "Milestone description."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "due_on",
          "description": {
            "zh": "截止时间，ISO 8601",
            "en": "Due date, ISO 8601."
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "delete_milestone",
      "description": {
        "zh": "删除 Milestone。",
        "en": "Delete a milestone."
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
          "name": "milestone_number",
          "description": {
            "zh": "Milestone 编号",
            "en": "Milestone number."
          },
          "type": "number",
          "required": true
        }
      ]
    },
    {
      "name": "get_branch_protection",
      "description": {
        "zh": "获取分支保护规则。",
        "en": "Get branch protection."
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
          "name": "branch",
          "description": {
            "zh": "分支名",
            "en": "Branch name."
          },
          "type": "string",
          "required": true
        }
      ]
    },
    {
      "name": "update_branch_protection",
      "description": {
        "zh": "更新分支保护规则。protection 需传 GitHub PUT 接口要求的完整 JSON。",
        "en": "Update branch protection. protection must be the complete JSON body required by the GitHub PUT endpoint."
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
          "name": "branch",
          "description": {
            "zh": "分支名",
            "en": "Branch name."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "protection",
          "description": {
            "zh": "分支保护 JSON 对象或 JSON 字符串",
            "en": "Branch protection JSON object or JSON string."
          },
          "type": "object",
          "required": true
        }
      ]
    },
    {
      "name": "delete_branch_protection",
      "description": {
        "zh": "删除分支保护规则。",
        "en": "Delete branch protection."
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
          "name": "branch",
          "description": {
            "zh": "分支名",
            "en": "Branch name."
          },
          "type": "string",
          "required": true
        }
      ]
    },
    {
      "name": "get_contributors_stats",
      "description": {
        "zh": "获取仓库贡献者统计。",
        "en": "Get repository contributor statistics."
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
      "name": "get_commit_activity",
      "description": {
        "zh": "获取最近一年按周提交活动。",
        "en": "Get the last year of weekly commit activity."
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
      "name": "get_code_frequency",
      "description": {
        "zh": "获取每周代码增删统计。",
        "en": "Get weekly code additions and deletions."
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
    }
  ]
}
*/

var __defProp = Object.defineProperty;
var __defProps = Object.defineProperties;
var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
var __getOwnPropDescs = Object.getOwnPropertyDescriptors;
var __getOwnPropNames = Object.getOwnPropertyNames;
var __getOwnPropSymbols = Object.getOwnPropertySymbols;
var __hasOwnProp = Object.prototype.hasOwnProperty;
var __propIsEnum = Object.prototype.propertyIsEnumerable;
var __defNormalProp = (obj, key, value) => key in obj ? __defProp(obj, key, { enumerable: true, configurable: true, writable: true, value }) : obj[key] = value;
var __spreadValues = (a, b) => {
  for (var prop in b || (b = {}))
    if (__hasOwnProp.call(b, prop))
      __defNormalProp(a, prop, b[prop]);
  if (__getOwnPropSymbols)
    for (var prop of __getOwnPropSymbols(b)) {
      if (__propIsEnum.call(b, prop))
        __defNormalProp(a, prop, b[prop]);
    }
  return a;
};
var __spreadProps = (a, b) => __defProps(a, __getOwnPropDescs(b));
var __export = (target, all) => {
  for (var name in all)
    __defProp(target, name, { get: all[name], enumerable: true });
};
var __copyProps = (to, from, except, desc) => {
  if (from && typeof from === "object" || typeof from === "function") {
    for (let key of __getOwnPropNames(from))
      if (!__hasOwnProp.call(to, key) && key !== except)
        __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
  }
  return to;
};
var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);

// src/index.ts
var index_exports = {};
__export(index_exports, {
  add_collaborator: () => add_collaborator,
  apply_local_delete: () => apply_local_delete,
  apply_local_replace: () => apply_local_replace,
  cancel_workflow_run: () => cancel_workflow_run,
  check_collaborator: () => check_collaborator,
  comment_issue: () => comment_issue,
  compare_refs: () => compare_refs,
  create_branch: () => create_branch,
  create_issue: () => create_issue,
  create_label: () => create_label,
  create_milestone: () => create_milestone,
  create_or_update_file: () => create_or_update_file,
  create_pull_request: () => create_pull_request,
  create_release: () => create_release,
  create_review: () => create_review,
  create_webhook: () => create_webhook,
  delete_branch: () => delete_branch,
  delete_branch_protection: () => delete_branch_protection,
  delete_file: () => delete_file,
  delete_label: () => delete_label,
  delete_milestone: () => delete_milestone,
  delete_release: () => delete_release,
  delete_release_asset: () => delete_release_asset,
  delete_webhook: () => delete_webhook,
  fork_repository: () => fork_repository,
  get_authenticated_user: () => get_authenticated_user,
  get_branch_protection: () => get_branch_protection,
  get_code_frequency: () => get_code_frequency,
  get_collaborator_permission: () => get_collaborator_permission,
  get_commit: () => get_commit,
  get_commit_activity: () => get_commit_activity,
  get_compare_diff: () => get_compare_diff,
  get_contributors_stats: () => get_contributors_stats,
  get_file_content: () => get_file_content,
  get_issue: () => get_issue,
  get_label: () => get_label,
  get_latest_release: () => get_latest_release,
  get_milestone: () => get_milestone,
  get_pull_request: () => get_pull_request,
  get_pull_request_diff: () => get_pull_request_diff,
  get_rate_limit: () => get_rate_limit,
  get_release: () => get_release,
  get_release_by_tag: () => get_release_by_tag,
  get_repository: () => get_repository,
  get_webhook: () => get_webhook,
  get_workflow_jobs: () => get_workflow_jobs,
  get_workflow_run: () => get_workflow_run,
  list_branches: () => list_branches,
  list_check_runs: () => list_check_runs,
  list_collaborators: () => list_collaborators,
  list_commits: () => list_commits,
  list_issue_comments: () => list_issue_comments,
  list_issues: () => list_issues,
  list_labels: () => list_labels,
  list_milestones: () => list_milestones,
  list_pull_request_commits: () => list_pull_request_commits,
  list_pull_request_files: () => list_pull_request_files,
  list_pull_request_reviews: () => list_pull_request_reviews,
  list_pull_requests: () => list_pull_requests,
  list_releases: () => list_releases,
  list_review_comments: () => list_review_comments,
  list_webhooks: () => list_webhooks,
  list_workflow_runs: () => list_workflow_runs,
  list_workflows: () => list_workflows,
  main: () => main,
  merge_pull_request: () => merge_pull_request,
  overwrite_local_file: () => overwrite_local_file,
  patch_file_in_repo: () => patch_file_in_repo,
  ping_webhook: () => ping_webhook,
  remove_collaborator: () => remove_collaborator,
  reply_review_comment: () => reply_review_comment,
  request_reviewers: () => request_reviewers,
  rerun_workflow_run: () => rerun_workflow_run,
  search_code: () => search_code,
  search_issues: () => search_issues,
  search_repositories: () => search_repositories,
  sync_fork: () => sync_fork,
  terminal_exec: () => terminal_exec,
  toolImpl: () => toolImpl,
  trigger_workflow: () => trigger_workflow,
  update_branch_protection: () => update_branch_protection,
  update_issue: () => update_issue,
  update_label: () => update_label,
  update_milestone: () => update_milestone,
  update_pull_request: () => update_pull_request,
  update_release: () => update_release,
  update_webhook: () => update_webhook,
  upload_release_asset: () => upload_release_asset
});
module.exports = __toCommonJS(index_exports);

// src/github/api.ts
function getBaseUrl() {
  const fromEnv = (typeof getEnv === "function" ? getEnv("GITHUB_API_BASE_URL") : void 0) || "";
  return fromEnv && String(fromEnv).trim() || "https://api.github.com";
}
function getUploadsBaseUrl() {
  const fromEnv = (typeof getEnv === "function" ? getEnv("GITHUB_UPLOADS_BASE_URL") : void 0) || "";
  const trimmed = String(fromEnv || "").trim().replace(/\/+$/, "");
  if (trimmed) return trimmed;
  const api = getBaseUrl().replace(/\/+$/, "");
  if (api === "https://api.github.com") return "https://uploads.github.com";
  return api;
}
function parseBool(value) {
  if (value === void 0 || value === null || value === "") return void 0;
  if (typeof value === "boolean") return value;
  const s = String(value).trim().toLowerCase();
  if (s === "true" || s === "1" || s === "yes") return true;
  if (s === "false" || s === "0" || s === "no") return false;
  return void 0;
}
function shellQuote(value) {
  return "'" + String(value != null ? value : "").replace(/'/g, "'''") + "'";
}
function getToken() {
  const token = (typeof getEnv === "function" ? getEnv("GITHUB_TOKEN") : void 0) || "";
  const trimmed = String(token || "").trim();
  return trimmed ? trimmed : void 0;
}
function requireToken(operation) {
  const token = getToken();
  if (!token) {
    throw new Error(`GITHUB_TOKEN is required for ${operation}.`);
  }
  return token;
}
function buildUrl(pathname, query) {
  const base = getBaseUrl().replace(/\/+$/, "");
  const path = pathname.startsWith("/") ? pathname : `/${pathname}`;
  const qs = [];
  if (query) {
    Object.keys(query).forEach((k) => {
      const v = query[k];
      if (v === void 0 || v === null || v === "") return;
      qs.push(`${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`);
    });
  }
  return qs.length > 0 ? `${base}${path}?${qs.join("&")}` : `${base}${path}`;
}
function repoPath(owner, repo, suffix = "") {
  const extra = suffix ? suffix.startsWith("/") ? suffix : `/${suffix}` : "";
  return `/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}${extra}`;
}
function encodeRef(ref) {
  return String(ref || "").split("/").map((part) => encodeURIComponent(part)).join("/");
}
function defaultHeaders(extra) {
  const headers = {
    Accept: "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
    "User-Agent": "Operit-GitHub"
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
function createHttpClient(timeoutMs = 3e4) {
  return OkHttp.newBuilder().connectTimeout(timeoutMs).readTimeout(timeoutMs).writeTimeout(timeoutMs).followRedirects(true).build();
}
async function requestRaw(options) {
  var _a;
  const client = createHttpClient((_a = options.timeoutMs) != null ? _a : 3e4);
  const req = client.newRequest().url(options.url).method(options.method);
  req.headers(defaultHeaders(options.headers));
  if (options.method !== "GET") {
    const payload = options.body === void 0 || options.body === null ? {} : options.body;
    req.body(JSON.stringify(payload), "json");
  }
  const resp = await req.build().execute();
  if (!resp.isSuccessful()) {
    throw new Error(`GitHub API Error: ${resp.statusCode} ${resp.statusMessage}
${resp.content}`);
  }
  return {
    statusCode: resp.statusCode,
    statusMessage: resp.statusMessage,
    headers: resp.headers || {},
    content: typeof resp.content === "string" ? resp.content : String(resp.content || "")
  };
}
async function requestJson(options) {
  const resp = await requestRaw(options);
  const text = String(resp.content || "").trim();
  if (resp.statusCode === 204 || !text) {
    return { ok: true, statusCode: resp.statusCode };
  }
  try {
    return JSON.parse(text);
  } catch (e) {
    throw new Error(`Failed to parse GitHub JSON response (${resp.statusCode}): ${text.slice(0, 500)}`);
  }
}
async function requestText(options) {
  const resp = await requestRaw(options);
  return {
    text: resp.content || "",
    statusCode: resp.statusCode,
    headers: resp.headers
  };
}
function parseJsonParam(value, fieldName) {
  if (value === void 0 || value === null || value === "") return void 0;
  if (typeof value === "object") return value;
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  if (!trimmed) return void 0;
  try {
    return JSON.parse(trimmed);
  } catch (e) {
    throw new Error(`${fieldName} must be valid JSON.`);
  }
}
function splitCsv(value) {
  if (value === void 0 || value === null || value === "") return void 0;
  if (Array.isArray(value)) {
    const items2 = value.map((it) => String(it).trim()).filter(Boolean);
    return items2.length > 0 ? items2 : void 0;
  }
  const items = String(value).split(",").map((it) => it.trim()).filter(Boolean);
  return items.length > 0 ? items : void 0;
}
function compactCommit(commit) {
  var _a, _b, _c, _d, _e, _f, _g, _h, _i, _j, _k;
  if (!commit) return commit;
  return {
    sha: commit.sha || ((_b = (_a = commit.commit) == null ? void 0 : _a.tree) == null ? void 0 : _b.sha),
    html_url: commit.html_url,
    message: ((_c = commit.commit) == null ? void 0 : _c.message) || commit.message,
    author: ((_e = (_d = commit.commit) == null ? void 0 : _d.author) == null ? void 0 : _e.name) || ((_f = commit.author) == null ? void 0 : _f.login),
    date: ((_h = (_g = commit.commit) == null ? void 0 : _g.author) == null ? void 0 : _h.date) || ((_j = (_i = commit.commit) == null ? void 0 : _i.committer) == null ? void 0 : _j.date),
    login: (_k = commit.author) == null ? void 0 : _k.login
  };
}
function compactFileChange(file, includePatch) {
  if (!file) return file;
  const item = {
    filename: file.filename,
    status: file.status,
    additions: file.additions,
    deletions: file.deletions,
    changes: file.changes,
    sha: file.sha,
    blob_url: file.blob_url,
    previous_filename: file.previous_filename
  };
  if (includePatch && typeof file.patch === "string") {
    item.patch = file.patch;
  }
  return item;
}
function truncateText(text, maxChars) {
  const src = String(text != null ? text : "");
  const limit = maxChars > 0 ? maxChars : src.length;
  if (src.length <= limit) {
    return { text: src, truncated: false, original_length: src.length };
  }
  const headChars = Math.max(200, Math.floor(limit * 0.35));
  const tailChars = Math.max(200, limit - headChars - 80);
  const omitted = src.length - headChars - tailChars;
  return {
    text: `${src.slice(0, headChars)}

... truncated ${omitted} chars ...

${src.slice(-tailChars)}`,
    truncated: true,
    original_length: src.length
  };
}

// src/github/issues.ts
function compactIssue(item) {
  var _a;
  if (!item) return item;
  return {
    number: item.number,
    title: item.title,
    state: item.state,
    html_url: item.html_url,
    user: (_a = item.user) == null ? void 0 : _a.login,
    labels: Array.isArray(item.labels) ? item.labels.map((it) => typeof it === "string" ? it : it.name) : [],
    assignees: Array.isArray(item.assignees) ? item.assignees.map((it) => it.login) : [],
    comments: item.comments,
    pull_request: Boolean(item.pull_request),
    created_at: item.created_at,
    updated_at: item.updated_at,
    body: item.body
  };
}
async function listIssues(params) {
  var _a, _b, _c;
  const url = buildUrl(repoPath(params.owner, params.repo, "/issues"), {
    state: (_a = params.state) != null ? _a : "open",
    labels: params.labels,
    creator: params.creator,
    page: (_b = params.page) != null ? _b : 1,
    per_page: (_c = params.per_page) != null ? _c : 30
  });
  const items = await requestJson({ method: "GET", url });
  const includePRs = params.include_pull_requests === true;
  const filtered = includePRs ? items : items.filter((it) => !it || !it.pull_request);
  return Array.isArray(filtered) ? filtered.map(compactIssue) : [];
}
async function getIssue(params) {
  const url = buildUrl(repoPath(params.owner, params.repo, `/issues/${params.issue_number}`));
  return compactIssue(await requestJson({ method: "GET", url }));
}
async function createIssue(params) {
  requireToken("create_issue");
  const url = buildUrl(repoPath(params.owner, params.repo, "/issues"));
  return compactIssue(
    await requestJson({
      method: "POST",
      url,
      body: {
        title: params.title,
        body: params.body,
        labels: splitCsv(params.labels),
        assignees: splitCsv(params.assignees)
      }
    })
  );
}
async function updateIssue(params) {
  requireToken("update_issue");
  const url = buildUrl(repoPath(params.owner, params.repo, `/issues/${params.issue_number}`));
  return compactIssue(
    await requestJson({
      method: "PATCH",
      url,
      body: {
        title: params.title,
        body: params.body,
        state: params.state,
        labels: splitCsv(params.labels),
        assignees: splitCsv(params.assignees)
      }
    })
  );
}
async function commentIssue(params) {
  var _a;
  requireToken("comment_issue");
  const url = buildUrl(repoPath(params.owner, params.repo, `/issues/${params.issue_number}/comments`));
  const data = await requestJson({ method: "POST", url, body: { body: params.body } });
  return {
    id: data.id,
    html_url: data.html_url,
    user: (_a = data.user) == null ? void 0 : _a.login,
    body: data.body,
    created_at: data.created_at
  };
}
async function listIssueComments(params) {
  var _a, _b;
  const url = buildUrl(repoPath(params.owner, params.repo, `/issues/${params.issue_number}/comments`), {
    page: (_a = params.page) != null ? _a : 1,
    per_page: (_b = params.per_page) != null ? _b : 30
  });
  const items = await requestJson({ method: "GET", url });
  return Array.isArray(items) ? items.map((item) => {
    var _a2;
    return {
      id: item.id,
      html_url: item.html_url,
      user: (_a2 = item.user) == null ? void 0 : _a2.login,
      body: item.body,
      created_at: item.created_at,
      updated_at: item.updated_at
    };
  }) : [];
}

// src/github/pulls.ts
function compactPull(pr) {
  var _a, _b, _c, _d, _e, _f;
  if (!pr) return pr;
  return {
    number: pr.number,
    title: pr.title,
    state: pr.state,
    draft: pr.draft,
    merged: pr.merged,
    mergeable: pr.mergeable,
    html_url: pr.html_url,
    user: (_a = pr.user) == null ? void 0 : _a.login,
    head: ((_b = pr.head) == null ? void 0 : _b.label) || ((_c = pr.head) == null ? void 0 : _c.ref),
    head_sha: (_d = pr.head) == null ? void 0 : _d.sha,
    base: ((_e = pr.base) == null ? void 0 : _e.label) || ((_f = pr.base) == null ? void 0 : _f.ref),
    body: pr.body,
    created_at: pr.created_at,
    updated_at: pr.updated_at
  };
}
async function listPullRequests(params) {
  var _a, _b, _c;
  const url = buildUrl(repoPath(params.owner, params.repo, "/pulls"), {
    state: (_a = params.state) != null ? _a : "open",
    head: params.head,
    base: params.base,
    page: (_b = params.page) != null ? _b : 1,
    per_page: (_c = params.per_page) != null ? _c : 30
  });
  const items = await requestJson({ method: "GET", url });
  return Array.isArray(items) ? items.map(compactPull) : [];
}
async function createPullRequest(params) {
  requireToken("create_pull_request");
  const url = buildUrl(repoPath(params.owner, params.repo, "/pulls"));
  return compactPull(
    await requestJson({
      method: "POST",
      url,
      body: {
        title: params.title,
        head: params.head,
        base: params.base,
        body: params.body,
        draft: params.draft
      }
    })
  );
}
async function getPullRequest(params) {
  const url = buildUrl(repoPath(params.owner, params.repo, `/pulls/${params.pull_number}`));
  return compactPull(await requestJson({ method: "GET", url }));
}
async function mergePullRequest(params) {
  requireToken("merge_pull_request");
  const url = buildUrl(repoPath(params.owner, params.repo, `/pulls/${params.pull_number}/merge`));
  return requestJson({
    method: "PUT",
    url,
    body: {
      commit_title: params.commit_title,
      commit_message: params.commit_message,
      merge_method: params.merge_method
    }
  });
}
async function updatePullRequest(params) {
  requireToken("update_pull_request");
  const url = buildUrl(repoPath(params.owner, params.repo, `/pulls/${params.pull_number}`));
  return compactPull(
    await requestJson({
      method: "PATCH",
      url,
      body: {
        title: params.title,
        body: params.body,
        state: params.state,
        base: params.base,
        draft: params.draft
      }
    })
  );
}
async function listPullRequestFiles(params) {
  var _a, _b;
  const url = buildUrl(repoPath(params.owner, params.repo, `/pulls/${params.pull_number}/files`), {
    page: (_a = params.page) != null ? _a : 1,
    per_page: (_b = params.per_page) != null ? _b : 100
  });
  const items = await requestJson({ method: "GET", url });
  return Array.isArray(items) ? items.map((file) => compactFileChange(file, params.include_patch === true)) : [];
}
async function getPullRequestDiff(params) {
  var _a;
  const url = buildUrl(repoPath(params.owner, params.repo, `/pulls/${params.pull_number}`));
  const resp = await requestText({
    method: "GET",
    url,
    headers: { Accept: "application/vnd.github.diff" },
    timeoutMs: 6e4
  });
  const truncated = truncateText(resp.text, (_a = params.max_chars) != null ? _a : 2e4);
  return {
    pull_number: params.pull_number,
    diff: truncated.text,
    truncated: truncated.truncated,
    original_length: truncated.original_length
  };
}
async function listPullRequestCommits(params) {
  var _a, _b;
  const url = buildUrl(repoPath(params.owner, params.repo, `/pulls/${params.pull_number}/commits`), {
    page: (_a = params.page) != null ? _a : 1,
    per_page: (_b = params.per_page) != null ? _b : 30
  });
  const items = await requestJson({ method: "GET", url });
  return Array.isArray(items) ? items.map(compactCommit) : [];
}
async function listPullRequestReviews(params) {
  var _a, _b;
  const url = buildUrl(repoPath(params.owner, params.repo, `/pulls/${params.pull_number}/reviews`), {
    page: (_a = params.page) != null ? _a : 1,
    per_page: (_b = params.per_page) != null ? _b : 30
  });
  const items = await requestJson({ method: "GET", url });
  return Array.isArray(items) ? items.map((item) => {
    var _a2;
    return {
      id: item.id,
      user: (_a2 = item.user) == null ? void 0 : _a2.login,
      state: item.state,
      body: item.body,
      submitted_at: item.submitted_at,
      html_url: item.html_url,
      commit_id: item.commit_id
    };
  }) : [];
}
async function listReviewComments(params) {
  var _a, _b;
  const url = buildUrl(repoPath(params.owner, params.repo, `/pulls/${params.pull_number}/comments`), {
    page: (_a = params.page) != null ? _a : 1,
    per_page: (_b = params.per_page) != null ? _b : 30
  });
  const items = await requestJson({ method: "GET", url });
  return Array.isArray(items) ? items.map((item) => {
    var _a2;
    return {
      id: item.id,
      user: (_a2 = item.user) == null ? void 0 : _a2.login,
      path: item.path,
      line: item.line,
      original_line: item.original_line,
      side: item.side,
      body: item.body,
      html_url: item.html_url,
      in_reply_to_id: item.in_reply_to_id,
      created_at: item.created_at,
      updated_at: item.updated_at
    };
  }) : [];
}
async function createReview(params) {
  var _a;
  requireToken("create_review");
  const comments = parseJsonParam(params.comments, "comments");
  const url = buildUrl(repoPath(params.owner, params.repo, `/pulls/${params.pull_number}/reviews`));
  const data = await requestJson({
    method: "POST",
    url,
    body: {
      body: params.body,
      event: params.event,
      commit_id: params.commit_id,
      comments
    }
  });
  return {
    id: data.id,
    state: data.state,
    body: data.body,
    html_url: data.html_url,
    user: (_a = data.user) == null ? void 0 : _a.login
  };
}
async function replyReviewComment(params) {
  var _a;
  requireToken("reply_review_comment");
  const url = buildUrl(
    repoPath(params.owner, params.repo, `/pulls/${params.pull_number}/comments/${params.comment_id}/replies`)
  );
  const data = await requestJson({
    method: "POST",
    url,
    body: { body: params.body }
  });
  return {
    id: data.id,
    body: data.body,
    html_url: data.html_url,
    in_reply_to_id: data.in_reply_to_id,
    user: (_a = data.user) == null ? void 0 : _a.login
  };
}
async function requestReviewers(params) {
  requireToken("request_reviewers");
  const url = buildUrl(repoPath(params.owner, params.repo, `/pulls/${params.pull_number}/requested_reviewers`));
  const data = await requestJson({
    method: "POST",
    url,
    body: {
      reviewers: splitCsv(params.reviewers),
      team_reviewers: splitCsv(params.team_reviewers)
    }
  });
  return {
    number: data.number,
    html_url: data.html_url,
    requested_reviewers: Array.isArray(data.requested_reviewers) ? data.requested_reviewers.map((it) => it.login) : [],
    requested_teams: Array.isArray(data.requested_teams) ? data.requested_teams.map((it) => it.slug || it.name) : []
  };
}

// src/utils/base64.ts
var BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
function normalizeBase64Input(value) {
  const cleaned = String(value || "").replace(/\s+/g, "").replace(/-/g, "+").replace(/_/g, "/");
  if (!cleaned) {
    return "";
  }
  const remainder = cleaned.length % 4;
  if (remainder === 1) {
    throw new Error("Invalid base64 string");
  }
  return remainder === 0 ? cleaned : cleaned + "=".repeat(4 - remainder);
}
function decodeBase64ToBytes(value) {
  const normalized = normalizeBase64Input(value);
  const bytes = [];
  for (let i = 0; i < normalized.length; i += 4) {
    const c1 = normalized[i];
    const c2 = normalized[i + 1];
    const c3 = normalized[i + 2];
    const c4 = normalized[i + 3];
    const v1 = BASE64_ALPHABET.indexOf(c1);
    const v2 = BASE64_ALPHABET.indexOf(c2);
    const v3 = c3 === "=" ? 0 : BASE64_ALPHABET.indexOf(c3);
    const v4 = c4 === "=" ? 0 : BASE64_ALPHABET.indexOf(c4);
    if (v1 < 0 || v2 < 0 || c3 !== "=" && v3 < 0 || c4 !== "=" && v4 < 0) {
      throw new Error("Invalid base64 string");
    }
    const chunk = v1 << 18 | v2 << 12 | v3 << 6 | v4;
    bytes.push(chunk >> 16 & 255);
    if (c3 !== "=") {
      bytes.push(chunk >> 8 & 255);
    }
    if (c4 !== "=") {
      bytes.push(chunk & 255);
    }
  }
  return bytes;
}
function encodeBytesToBase64(bytes) {
  let output = "";
  for (let i = 0; i < bytes.length; i += 3) {
    const b1 = bytes[i];
    const hasB2 = i + 1 < bytes.length;
    const hasB3 = i + 2 < bytes.length;
    const b2 = hasB2 ? bytes[i + 1] : 0;
    const b3 = hasB3 ? bytes[i + 2] : 0;
    const chunk = b1 << 16 | b2 << 8 | b3;
    output += BASE64_ALPHABET[chunk >> 18 & 63];
    output += BASE64_ALPHABET[chunk >> 12 & 63];
    output += hasB2 ? BASE64_ALPHABET[chunk >> 6 & 63] : "=";
    output += hasB3 ? BASE64_ALPHABET[chunk & 63] : "=";
  }
  return output;
}
function utf8Encode(text) {
  const bytes = [];
  const input = String(text != null ? text : "");
  for (let i = 0; i < input.length; i++) {
    const codePoint = input.codePointAt(i);
    if (codePoint == null) {
      continue;
    }
    if (codePoint > 65535) {
      i += 1;
    }
    if (codePoint <= 127) {
      bytes.push(codePoint);
    } else if (codePoint <= 2047) {
      bytes.push(
        192 | codePoint >> 6,
        128 | codePoint & 63
      );
    } else if (codePoint <= 65535) {
      bytes.push(
        224 | codePoint >> 12,
        128 | codePoint >> 6 & 63,
        128 | codePoint & 63
      );
    } else {
      bytes.push(
        240 | codePoint >> 18,
        128 | codePoint >> 12 & 63,
        128 | codePoint >> 6 & 63,
        128 | codePoint & 63
      );
    }
  }
  return bytes;
}
function readContinuationByte(bytes, index) {
  const value = bytes[index];
  if (value == null || (value & 192) !== 128) {
    throw new Error("Invalid UTF-8 sequence");
  }
  return value & 63;
}
function utf8Decode(bytes) {
  let output = "";
  for (let i = 0; i < bytes.length; i++) {
    const first = bytes[i];
    if (first == null) {
      break;
    }
    if (first <= 127) {
      output += String.fromCodePoint(first);
      continue;
    }
    if ((first & 224) === 192) {
      const codePoint = (first & 31) << 6 | readContinuationByte(bytes, ++i);
      output += String.fromCodePoint(codePoint);
      continue;
    }
    if ((first & 240) === 224) {
      const codePoint = (first & 15) << 12 | readContinuationByte(bytes, ++i) << 6 | readContinuationByte(bytes, ++i);
      output += String.fromCodePoint(codePoint);
      continue;
    }
    if ((first & 248) === 240) {
      const codePoint = (first & 7) << 18 | readContinuationByte(bytes, ++i) << 12 | readContinuationByte(bytes, ++i) << 6 | readContinuationByte(bytes, ++i);
      output += String.fromCodePoint(codePoint);
      continue;
    }
    throw new Error("Invalid UTF-8 sequence");
  }
  return output;
}
function safeAtobBase64(b64) {
  return utf8Decode(decodeBase64ToBytes(b64));
}
function safeBtoaBase64(text) {
  return encodeBytesToBase64(utf8Encode(String(text != null ? text : "")));
}

// src/github/contents.ts
async function getFileContent(params) {
  const url = buildUrl(
    `/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/contents/${params.path.split("/").map((p) => encodeURIComponent(p)).join("/")}`,
    { ref: params.ref }
  );
  const data = await requestJson({ method: "GET", url });
  if (data && data.type === "file" && typeof data.content === "string" && data.encoding === "base64") {
    const decoded = safeAtobBase64(data.content);
    return __spreadProps(__spreadValues({}, data), {
      decoded_text: decoded
    });
  }
  return data;
}
async function resolveFileSha(params) {
  try {
    const existing = await getFileContent({
      owner: params.owner,
      repo: params.repo,
      path: params.path,
      ref: params.branch
    });
    const sha = existing && typeof existing.sha === "string" ? existing.sha : void 0;
    return sha;
  } catch (e) {
    return void 0;
  }
}
async function createOrUpdateFile(params) {
  var _a;
  requireToken("create_or_update_file");
  const encoding = (params.content_encoding || "utf-8").toLowerCase();
  const base64Content = encoding === "base64" ? params.content : safeBtoaBase64(params.content);
  const sha = (_a = params.sha) != null ? _a : await resolveFileSha({ owner: params.owner, repo: params.repo, path: params.path, branch: params.branch });
  const url = buildUrl(
    `/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/contents/${params.path.split("/").map((p) => encodeURIComponent(p)).join("/")}`
  );
  return requestJson({
    method: "PUT",
    url,
    body: {
      message: params.message,
      content: base64Content,
      branch: params.branch,
      sha
    }
  });
}
async function deleteFile(params) {
  var _a;
  requireToken("delete_file");
  const sha = (_a = params.sha) != null ? _a : await resolveFileSha({ owner: params.owner, repo: params.repo, path: params.path, branch: params.branch });
  if (!sha) {
    throw new Error("File sha is required (unable to resolve automatically).");
  }
  const url = buildUrl(
    `/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/contents/${params.path.split("/").map((p) => encodeURIComponent(p)).join("/")}`
  );
  return requestJson({
    method: "DELETE",
    url,
    body: {
      message: params.message,
      branch: params.branch,
      sha
    }
  });
}

// src/github/repos.ts
async function searchRepositories(params) {
  var _a, _b;
  const page = (_a = params.page) != null ? _a : 1;
  const perPage = (_b = params.per_page) != null ? _b : 30;
  const url = buildUrl("/search/repositories", {
    q: params.query,
    sort: params.sort,
    order: params.order,
    page,
    per_page: perPage
  });
  return requestJson({ method: "GET", url });
}
async function getRepository(params) {
  const url = buildUrl(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}`);
  return requestJson({ method: "GET", url });
}

// src/github/branches.ts
function requiredName(value, field) {
  const name = String(value != null ? value : "").trim();
  if (!name) {
    throw new Error(`${field} is required`);
  }
  return name;
}
async function getBranchHeadSha(params) {
  var _a;
  const url = buildUrl(repoPath(params.owner, params.repo, `/git/ref/heads/${encodeRef(params.branch)}`));
  const data = await requestJson({ method: "GET", url });
  const sha = (_a = data == null ? void 0 : data.object) == null ? void 0 : _a.sha;
  if (!sha) {
    throw new Error(`Cannot resolve branch sha for ${params.branch}`);
  }
  return sha;
}
async function createBranch(params) {
  var _a, _b, _c;
  requireToken("create_branch");
  const newBranch = requiredName(params.new_branch, "new_branch");
  const fromSha = String((_a = params.from_sha) != null ? _a : "").trim();
  let sha = fromSha;
  let fromBranch;
  if (!sha) {
    fromBranch = String((_b = params.from_branch) != null ? _b : "").trim() || String(((_c = await getRepository({ owner: params.owner, repo: params.repo })) == null ? void 0 : _c.default_branch) || "main");
    sha = await getBranchHeadSha({ owner: params.owner, repo: params.repo, branch: fromBranch });
  }
  const url = buildUrl(repoPath(params.owner, params.repo, "/git/refs"));
  const result = await requestJson({
    method: "POST",
    url,
    body: {
      ref: `refs/heads/${newBranch}`,
      sha
    }
  });
  return __spreadProps(__spreadValues({}, result), {
    new_branch: newBranch,
    from_sha: sha,
    from_branch: fromBranch
  });
}
async function deleteBranch(params) {
  requireToken("delete_branch");
  const branch = requiredName(params.branch, "branch");
  const repoInfo = await getRepository({ owner: params.owner, repo: params.repo });
  const defaultBranch = String((repoInfo == null ? void 0 : repoInfo.default_branch) || "").trim();
  if (defaultBranch && branch === defaultBranch) {
    throw new Error(`Refusing to delete the default branch '${defaultBranch}'.`);
  }
  const url = buildUrl(repoPath(params.owner, params.repo, `/git/refs/heads/${encodeRef(branch)}`));
  const result = await requestJson({ method: "DELETE", url });
  return { ok: true, branch, result };
}

// src/github/git.ts
async function listBranches(params) {
  var _a, _b;
  const url = buildUrl(repoPath(params.owner, params.repo, "/branches"), {
    protected: params.protected_only,
    page: (_a = params.page) != null ? _a : 1,
    per_page: (_b = params.per_page) != null ? _b : 30
  });
  const items = await requestJson({ method: "GET", url });
  return Array.isArray(items) ? items.map((item) => {
    var _a2;
    return {
      name: item.name,
      sha: (_a2 = item.commit) == null ? void 0 : _a2.sha,
      protected: item.protected,
      html_url: `https://github.com/${params.owner}/${params.repo}/tree/${item.name}`
    };
  }) : [];
}
async function listCommits(params) {
  var _a, _b;
  const url = buildUrl(repoPath(params.owner, params.repo, "/commits"), {
    sha: params.sha,
    path: params.path,
    author: params.author,
    page: (_a = params.page) != null ? _a : 1,
    per_page: (_b = params.per_page) != null ? _b : 20
  });
  const items = await requestJson({ method: "GET", url });
  return Array.isArray(items) ? items.map(compactCommit) : [];
}
async function getCommit(params) {
  const url = buildUrl(repoPath(params.owner, params.repo, `/commits/${encodeURIComponent(params.ref)}`));
  const data = await requestJson({ method: "GET", url });
  return __spreadProps(__spreadValues({}, compactCommit(data)), {
    stats: data.stats,
    files: params.include_files === false ? void 0 : Array.isArray(data.files) ? data.files.map((file) => compactFileChange(file, false)) : []
  });
}
async function compareRefs(params) {
  const spec = `${encodeRef(params.base)}...${encodeRef(params.head)}`;
  const url = buildUrl(repoPath(params.owner, params.repo, `/compare/${spec}`));
  const data = await requestJson({ method: "GET", url });
  const includeFiles = params.include_files !== false;
  return {
    status: data.status,
    ahead_by: data.ahead_by,
    behind_by: data.behind_by,
    total_commits: data.total_commits,
    html_url: data.html_url,
    permalink_url: data.permalink_url,
    base_commit: compactCommit(data.base_commit),
    merge_base_commit: compactCommit(data.merge_base_commit),
    commits: Array.isArray(data.commits) ? data.commits.map(compactCommit) : [],
    files: includeFiles ? Array.isArray(data.files) ? data.files.map((file) => compactFileChange(file, params.include_patch === true)) : [] : void 0
  };
}
async function getCompareDiff(params) {
  var _a;
  const spec = `${encodeRef(params.base)}...${encodeRef(params.head)}`;
  const url = buildUrl(repoPath(params.owner, params.repo, `/compare/${spec}`));
  const resp = await requestText({
    method: "GET",
    url,
    headers: { Accept: "application/vnd.github.diff" },
    timeoutMs: 6e4
  });
  const truncated = truncateText(resp.text, (_a = params.max_chars) != null ? _a : 2e4);
  return {
    base: params.base,
    head: params.head,
    diff: truncated.text,
    truncated: truncated.truncated,
    original_length: truncated.original_length
  };
}

// src/github/actions.ts
function compactWorkflow(item) {
  if (!item) return item;
  return {
    id: item.id,
    name: item.name,
    path: item.path,
    state: item.state,
    html_url: item.html_url,
    badge_url: item.badge_url,
    created_at: item.created_at,
    updated_at: item.updated_at
  };
}
function compactRun(item) {
  var _a, _b;
  if (!item) return item;
  return {
    id: item.id,
    name: item.name,
    display_title: item.display_title,
    status: item.status,
    conclusion: item.conclusion,
    event: item.event,
    head_branch: item.head_branch,
    head_sha: item.head_sha,
    html_url: item.html_url,
    path: item.path,
    run_number: item.run_number,
    run_attempt: item.run_attempt,
    created_at: item.created_at,
    updated_at: item.updated_at,
    actor: (_a = item.actor) == null ? void 0 : _a.login,
    triggering_actor: (_b = item.triggering_actor) == null ? void 0 : _b.login
  };
}
function compactJob(item) {
  if (!item) return item;
  return {
    id: item.id,
    run_id: item.run_id,
    name: item.name,
    status: item.status,
    conclusion: item.conclusion,
    html_url: item.html_url,
    started_at: item.started_at,
    completed_at: item.completed_at,
    runner_name: item.runner_name,
    steps: Array.isArray(item.steps) ? item.steps.map((step) => ({
      name: step.name,
      status: step.status,
      conclusion: step.conclusion,
      number: step.number,
      started_at: step.started_at,
      completed_at: step.completed_at
    })) : []
  };
}
async function listWorkflows(params) {
  var _a, _b;
  const url = buildUrl(repoPath(params.owner, params.repo, "/actions/workflows"), {
    page: (_a = params.page) != null ? _a : 1,
    per_page: (_b = params.per_page) != null ? _b : 30
  });
  const data = await requestJson({ method: "GET", url });
  return {
    total_count: data == null ? void 0 : data.total_count,
    workflows: Array.isArray(data == null ? void 0 : data.workflows) ? data.workflows.map(compactWorkflow) : []
  };
}
async function listWorkflowRuns(params) {
  var _a, _b;
  const suffix = params.workflow_id ? `/actions/workflows/${encodeURIComponent(String(params.workflow_id))}/runs` : "/actions/runs";
  const url = buildUrl(repoPath(params.owner, params.repo, suffix), {
    branch: params.branch,
    status: params.status,
    event: params.event,
    page: (_a = params.page) != null ? _a : 1,
    per_page: (_b = params.per_page) != null ? _b : 20
  });
  const data = await requestJson({ method: "GET", url });
  return {
    total_count: data == null ? void 0 : data.total_count,
    workflow_runs: Array.isArray(data == null ? void 0 : data.workflow_runs) ? data.workflow_runs.map(compactRun) : []
  };
}
async function getWorkflowRun(params) {
  const url = buildUrl(repoPath(params.owner, params.repo, `/actions/runs/${encodeURIComponent(String(params.run_id))}`));
  return compactRun(await requestJson({ method: "GET", url }));
}
async function triggerWorkflow(params) {
  requireToken("trigger_workflow");
  const url = buildUrl(
    repoPath(params.owner, params.repo, `/actions/workflows/${encodeURIComponent(String(params.workflow_id))}/dispatches`)
  );
  const inputs = parseJsonParam(params.inputs, "inputs");
  const result = await requestJson({
    method: "POST",
    url,
    body: {
      ref: params.ref,
      inputs
    }
  });
  return {
    ok: true,
    workflow_id: params.workflow_id,
    ref: params.ref,
    inputs: inputs || {},
    dispatch: result
  };
}
async function getWorkflowJobs(params) {
  var _a, _b, _c;
  const url = buildUrl(repoPath(params.owner, params.repo, `/actions/runs/${encodeURIComponent(String(params.run_id))}/jobs`), {
    page: (_a = params.page) != null ? _a : 1,
    per_page: (_b = params.per_page) != null ? _b : 50
  });
  const data = await requestJson({ method: "GET", url });
  const jobs = Array.isArray(data == null ? void 0 : data.jobs) ? data.jobs.map(compactJob) : [];
  const selected = params.failed_only ? jobs.filter((job) => job.conclusion && job.conclusion !== "success" && job.conclusion !== "skipped") : jobs;
  if (!params.include_logs) {
    return {
      total_count: data == null ? void 0 : data.total_count,
      jobs: selected
    };
  }
  const maxLogChars = (_c = params.max_log_chars) != null ? _c : 8e3;
  const withLogs = [];
  for (const job of selected) {
    try {
      const logUrl = buildUrl(repoPath(params.owner, params.repo, `/actions/jobs/${encodeURIComponent(String(job.id))}/logs`));
      const logResp = await requestText({ method: "GET", url: logUrl, timeoutMs: 6e4 });
      const truncated = truncateText(logResp.text, maxLogChars);
      withLogs.push(__spreadProps(__spreadValues({}, job), {
        logs: truncated.text,
        logs_truncated: truncated.truncated,
        logs_original_length: truncated.original_length
      }));
    } catch (e) {
      withLogs.push(__spreadProps(__spreadValues({}, job), {
        logs_error: String(e && e.message ? e.message : e)
      }));
    }
  }
  return {
    total_count: data == null ? void 0 : data.total_count,
    jobs: withLogs
  };
}
async function rerunWorkflowRun(params) {
  requireToken("rerun_workflow_run");
  const suffix = params.failed_jobs_only ? `/actions/runs/${encodeURIComponent(String(params.run_id))}/rerun-failed-jobs` : `/actions/runs/${encodeURIComponent(String(params.run_id))}/rerun`;
  const url = buildUrl(repoPath(params.owner, params.repo, suffix));
  const body = params.enable_debug_logging === void 0 ? void 0 : { enable_debug_logging: params.enable_debug_logging };
  const result = await requestJson({ method: "POST", url, body });
  return { ok: true, run_id: params.run_id, failed_jobs_only: Boolean(params.failed_jobs_only), result };
}
async function cancelWorkflowRun(params) {
  requireToken("cancel_workflow_run");
  const url = buildUrl(repoPath(params.owner, params.repo, `/actions/runs/${encodeURIComponent(String(params.run_id))}/cancel`));
  const result = await requestJson({ method: "POST", url });
  return { ok: true, run_id: params.run_id, result };
}
async function listCheckRuns(params) {
  var _a, _b;
  const url = buildUrl(repoPath(params.owner, params.repo, `/commits/${encodeURIComponent(params.ref)}/check-runs`), {
    status: params.status,
    filter: params.filter,
    page: (_a = params.page) != null ? _a : 1,
    per_page: (_b = params.per_page) != null ? _b : 30
  });
  const data = await requestJson({ method: "GET", url });
  return {
    total_count: data == null ? void 0 : data.total_count,
    check_runs: Array.isArray(data == null ? void 0 : data.check_runs) ? data.check_runs.map((item) => {
      var _a2, _b2;
      return {
        id: item.id,
        name: item.name,
        status: item.status,
        conclusion: item.conclusion,
        html_url: item.html_url,
        details_url: item.details_url,
        started_at: item.started_at,
        completed_at: item.completed_at,
        app: ((_a2 = item.app) == null ? void 0 : _a2.slug) || ((_b2 = item.app) == null ? void 0 : _b2.name)
      };
    }) : []
  };
}

// src/github/search.ts
async function searchCode(params) {
  var _a, _b;
  const url = buildUrl("/search/code", {
    q: params.query,
    sort: params.sort,
    order: params.order,
    page: (_a = params.page) != null ? _a : 1,
    per_page: (_b = params.per_page) != null ? _b : 20
  });
  const data = await requestJson({
    method: "GET",
    url,
    headers: { Accept: "application/vnd.github.text-match+json" }
  });
  return {
    total_count: data == null ? void 0 : data.total_count,
    incomplete_results: data == null ? void 0 : data.incomplete_results,
    items: Array.isArray(data == null ? void 0 : data.items) ? data.items.map((item) => {
      var _a2;
      return {
        name: item.name,
        path: item.path,
        sha: item.sha,
        html_url: item.html_url,
        repository: (_a2 = item.repository) == null ? void 0 : _a2.full_name,
        score: item.score,
        text_matches: Array.isArray(item.text_matches) ? item.text_matches.map((match) => ({
          fragment: match.fragment,
          property: match.property
        })) : []
      };
    }) : []
  };
}
async function searchIssues(params) {
  var _a, _b;
  const url = buildUrl("/search/issues", {
    q: params.query,
    sort: params.sort,
    order: params.order,
    page: (_a = params.page) != null ? _a : 1,
    per_page: (_b = params.per_page) != null ? _b : 20
  });
  const data = await requestJson({ method: "GET", url });
  return {
    total_count: data == null ? void 0 : data.total_count,
    incomplete_results: data == null ? void 0 : data.incomplete_results,
    items: Array.isArray(data == null ? void 0 : data.items) ? data.items.map((item) => {
      var _a2;
      return {
        number: item.number,
        title: item.title,
        state: item.state,
        html_url: item.html_url,
        repository: item.repository_url,
        user: (_a2 = item.user) == null ? void 0 : _a2.login,
        comments: item.comments,
        pull_request: Boolean(item.pull_request),
        updated_at: item.updated_at
      };
    }) : []
  };
}

// src/github/users.ts
async function getAuthenticatedUser() {
  var _a;
  const data = await requestJson({ method: "GET", url: buildUrl("/user") });
  return {
    login: data.login,
    id: data.id,
    name: data.name,
    html_url: data.html_url,
    type: data.type,
    company: data.company,
    public_repos: data.public_repos,
    total_private_repos: data.total_private_repos,
    plan: (_a = data.plan) == null ? void 0 : _a.name
  };
}
async function getRateLimit() {
  var _a, _b, _c;
  const data = await requestJson({ method: "GET", url: buildUrl("/rate_limit") });
  const core = ((_a = data == null ? void 0 : data.resources) == null ? void 0 : _a.core) || {};
  const search = ((_b = data == null ? void 0 : data.resources) == null ? void 0 : _b.search) || {};
  const graphql = ((_c = data == null ? void 0 : data.resources) == null ? void 0 : _c.graphql) || {};
  return {
    core: {
      limit: core.limit,
      remaining: core.remaining,
      reset: core.reset,
      used: core.used
    },
    search: {
      limit: search.limit,
      remaining: search.remaining,
      reset: search.reset,
      used: search.used
    },
    graphql: {
      limit: graphql.limit,
      remaining: graphql.remaining,
      reset: graphql.reset,
      used: graphql.used
    }
  };
}

// src/github/forks.ts
async function forkRepository(params) {
  var _a, _b;
  requireToken("fork_repository");
  const url = buildUrl(repoPath(params.owner, params.repo, "/forks"));
  const data = await requestJson({
    method: "POST",
    url,
    body: {
      organization: params.organization,
      name: params.name,
      default_branch_only: params.default_branch_only
    }
  });
  return {
    full_name: data.full_name,
    html_url: data.html_url,
    default_branch: data.default_branch,
    parent: (_a = data.parent) == null ? void 0 : _a.full_name,
    source: (_b = data.source) == null ? void 0 : _b.full_name,
    private: data.private
  };
}
async function syncFork(params) {
  requireToken("sync_fork");
  const url = buildUrl(repoPath(params.owner, params.repo, "/merge-upstream"));
  const data = await requestJson({
    method: "POST",
    url,
    body: {
      branch: params.branch
    }
  });
  return {
    message: data.message,
    merge_type: data.merge_type,
    base_branch: data.base_branch
  };
}

// src/github/patch.ts
function normalizeNewlines(text) {
  return String(text != null ? text : "").replace(/\r\n/g, "\n");
}
function parseBlocks(patch) {
  const src = normalizeNewlines(patch);
  const ops = [];
  const blockRegex = /\[START-(REPLACE|DELETE)\]\s*\n([\s\S]*?)\[END-\1\]/g;
  let match;
  while ((match = blockRegex.exec(src)) !== null) {
    const action = match[1];
    const body = match[2] || "";
    const oldMatch = /\[OLD\]\s*\n([\s\S]*?)\n\[\/OLD\]/.exec(body);
    if (!oldMatch) {
      throw new Error(`Patch block missing [OLD] section for ${action}`);
    }
    const oldContent = oldMatch[1];
    let newContent = "";
    if (action === "REPLACE") {
      const newMatch = /\[NEW\]\s*\n([\s\S]*?)\n\[\/NEW\]/.exec(body);
      if (!newMatch) {
        throw new Error("REPLACE block missing [NEW] section");
      }
      newContent = newMatch[1];
    }
    ops.push({ action, oldContent, newContent });
  }
  if (ops.length === 0) {
    throw new Error("No patch blocks found. Use [START-REPLACE]/[START-DELETE] blocks.");
  }
  return ops;
}
function applyOps(original, ops) {
  let out = normalizeNewlines(original);
  for (const op of ops) {
    if (!out.includes(op.oldContent)) {
      throw new Error("OLD block content not found in target file.");
    }
    if (op.action === "DELETE") {
      out = out.replace(op.oldContent, "");
    } else {
      out = out.replace(op.oldContent, op.newContent);
    }
  }
  return out;
}
async function patchFileInRepo(params) {
  const file = await getFileContent({
    owner: params.owner,
    repo: params.repo,
    path: params.path,
    ref: params.branch
  });
  const originalText = typeof (file == null ? void 0 : file.decoded_text) === "string" ? file.decoded_text : "";
  const sha = typeof (file == null ? void 0 : file.sha) === "string" ? file.sha : void 0;
  if (!sha) {
    throw new Error("Cannot patch file: sha missing (is it a file path?)");
  }
  const ops = parseBlocks(params.patch);
  const patched = applyOps(originalText, ops);
  return createOrUpdateFile({
    owner: params.owner,
    repo: params.repo,
    path: params.path,
    message: params.message,
    content: patched,
    content_encoding: "utf-8",
    branch: params.branch,
    sha
  });
}

// src/github/local/fileApply.ts
async function applyLocalReplace(params) {
  return Tools.Files.apply(params.path, "replace", params.old, params.new, params.environment);
}
async function applyLocalDelete(params) {
  return Tools.Files.apply(params.path, "delete", params.old, void 0, params.environment);
}
async function overwriteLocalFile(params) {
  var _a;
  const exists = await Tools.Files.exists(params.path, params.environment);
  if (exists.exists) {
    await Tools.Files.deleteFile(params.path, true, params.environment);
  }
  return Tools.Files.write(params.path, String((_a = params.content) != null ? _a : ""), false, params.environment);
}

// src/github/local/terminal.ts
var terminalSessionId = null;
async function getTerminalSession(sessionName) {
  if (terminalSessionId) return terminalSessionId;
  const session = await Tools.System.terminal.create(sessionName || "github_tools_session");
  terminalSessionId = session.sessionId;
  return terminalSessionId;
}
async function terminalExec(params) {
  const sessionId = await getTerminalSession(params.session_name);
  const result = await Tools.System.terminal.exec(sessionId, params.command);
  if (params.close) {
    await Tools.System.terminal.close(sessionId);
    terminalSessionId = null;
  }
  return result;
}

// src/github/releases.ts
function compactAsset(item) {
  var _a;
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
    uploader: (_a = item.uploader) == null ? void 0 : _a.login
  };
}
function compactRelease(item) {
  var _a;
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
    author: (_a = item.author) == null ? void 0 : _a.login,
    assets: Array.isArray(item.assets) ? item.assets.map(compactAsset) : []
  };
}
async function listReleases(params) {
  var _a, _b;
  const url = buildUrl(repoPath(params.owner, params.repo, "/releases"), {
    page: (_a = params.page) != null ? _a : 1,
    per_page: (_b = params.per_page) != null ? _b : 30
  });
  const items = await requestJson({ method: "GET", url });
  return Array.isArray(items) ? items.map(compactRelease) : [];
}
async function getRelease(params) {
  const url = buildUrl(repoPath(params.owner, params.repo, `/releases/${encodeURIComponent(String(params.release_id))}`));
  return compactRelease(await requestJson({ method: "GET", url }));
}
async function getLatestRelease(params) {
  const url = buildUrl(repoPath(params.owner, params.repo, "/releases/latest"));
  return compactRelease(await requestJson({ method: "GET", url }));
}
async function getReleaseByTag(params) {
  const url = buildUrl(repoPath(params.owner, params.repo, `/releases/tags/${encodeURIComponent(params.tag)}`));
  return compactRelease(await requestJson({ method: "GET", url }));
}
async function createRelease(params) {
  requireToken("create_release");
  const url = buildUrl(repoPath(params.owner, params.repo, "/releases"));
  return compactRelease(
    await requestJson({
      method: "POST",
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
async function updateRelease(params) {
  requireToken("update_release");
  const url = buildUrl(repoPath(params.owner, params.repo, `/releases/${encodeURIComponent(String(params.release_id))}`));
  return compactRelease(
    await requestJson({
      method: "PATCH",
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
async function deleteRelease(params) {
  requireToken("delete_release");
  const url = buildUrl(repoPath(params.owner, params.repo, `/releases/${encodeURIComponent(String(params.release_id))}`));
  const result = await requestJson({ method: "DELETE", url });
  return { ok: true, release_id: params.release_id, result };
}
function guessFileName(path) {
  const parts = String(path || "").replace(/\\/g, "/").split("/");
  return parts.filter(Boolean).pop() || "asset.bin";
}
async function uploadReleaseAsset(params) {
  const token = requireToken("upload_release_asset");
  const name = params.name || guessFileName(params.asset_path);
  const contentType = params.content_type || "application/octet-stream";
  const environment = params.environment || "android";
  const sourcePath = String(params.asset_path || "").trim();
  if (!sourcePath) {
    throw new Error("asset_path is required");
  }
  const exists = await Tools.Files.exists(sourcePath, environment);
  if (!exists.exists) {
    throw new Error(`Asset file not found: ${sourcePath}`);
  }
  let linuxPath = sourcePath;
  if (environment !== "linux") {
    linuxPath = `/tmp/operit-github-asset-${Date.now()}-${name}`;
    await Tools.Files.copy(sourcePath, linuxPath, false, environment, "linux");
  }
  const uploadsPath = repoPath(
    params.owner,
    params.repo,
    `/releases/${encodeURIComponent(String(params.release_id))}/assets`
  );
  const qs = [`name=${encodeURIComponent(name)}`];
  if (params.label) qs.push(`label=${encodeURIComponent(params.label)}`);
  const finalUrl = `${getUploadsBaseUrl()}${uploadsPath}?${qs.join("&")}`;
  const headerFile = `/tmp/operit-github-upload-headers-${Date.now()}`;
  await Tools.Files.write(
    headerFile,
    [
      'header = "Accept: application/vnd.github+json"',
      `header = "Authorization: Bearer ${token}"`,
      'header = "X-GitHub-Api-Version: 2022-11-28"',
      `header = "Content-Type: ${contentType}"`,
      ""
    ].join("\n"),
    false,
    "linux"
  );
  const command = [
    "curl",
    "-sS",
    "-X",
    "POST",
    "-K",
    shellQuote(headerFile),
    "--data-binary",
    "@" + shellQuote(linuxPath),
    "-w",
    shellQuote("\nHTTP_STATUS:%{http_code}"),
    shellQuote(finalUrl)
  ].join(" ");
  const sessionId = await getTerminalSession("github_tools_session");
  let result;
  try {
    result = await Tools.System.terminal.exec(sessionId, command, 12e4);
  } finally {
    try {
      await Tools.Files.deleteFile(headerFile, false, "linux");
    } catch (e) {
    }
  }
  const output = String(result.output || "");
  const statusMatch = output.match(/HTTP_STATUS:(\d+)\s*$/);
  const statusCode = statusMatch ? Number(statusMatch[1]) : result.exitCode === 0 ? 200 : 500;
  const bodyText = statusMatch ? output.slice(0, statusMatch.index).trim() : output.trim();
  if (statusCode < 200 || statusCode >= 300) {
    throw new Error(`GitHub API Error: ${statusCode}
${bodyText.slice(0, 800)}`);
  }
  try {
    return compactAsset(JSON.parse(bodyText));
  } catch (e) {
    throw new Error(`Failed to parse upload response: ${bodyText.slice(0, 500)}`);
  }
}
async function deleteReleaseAsset(params) {
  requireToken("delete_release_asset");
  const url = buildUrl(repoPath(params.owner, params.repo, `/releases/assets/${encodeURIComponent(String(params.asset_id))}`));
  const result = await requestJson({ method: "DELETE", url });
  return { ok: true, asset_id: params.asset_id, result };
}

// src/github/webhooks.ts
function compactWebhook(item) {
  if (!item) return item;
  const config = item.config || {};
  return {
    id: item.id,
    name: item.name,
    active: item.active,
    events: item.events,
    config: {
      url: config.url,
      content_type: config.content_type,
      insecure_ssl: config.insecure_ssl
    },
    created_at: item.created_at,
    updated_at: item.updated_at,
    ping_url: item.ping_url,
    test_url: item.test_url,
    last_response: item.last_response
  };
}
async function listWebhooks(params) {
  var _a, _b;
  requireToken("list_webhooks");
  const url = buildUrl(repoPath(params.owner, params.repo, "/hooks"), {
    page: (_a = params.page) != null ? _a : 1,
    per_page: (_b = params.per_page) != null ? _b : 30
  });
  const items = await requestJson({ method: "GET", url });
  return Array.isArray(items) ? items.map(compactWebhook) : [];
}
async function getWebhook(params) {
  requireToken("get_webhook");
  const url = buildUrl(repoPath(params.owner, params.repo, `/hooks/${encodeURIComponent(String(params.hook_id))}`));
  return compactWebhook(await requestJson({ method: "GET", url }));
}
async function createWebhook(params) {
  requireToken("create_webhook");
  const parsedConfig = parseJsonParam(params.config, "config") || {};
  const insecure = parseBool(params.insecure_ssl);
  const url = buildUrl(repoPath(params.owner, params.repo, "/hooks"));
  return compactWebhook(
    await requestJson({
      method: "POST",
      url,
      body: {
        name: "web",
        active: parseBool(params.active),
        events: splitCsv(params.events),
        config: {
          url: params.url || parsedConfig.url,
          content_type: params.content_type || parsedConfig.content_type || "json",
          secret: params.secret || parsedConfig.secret,
          insecure_ssl: insecure === void 0 ? parsedConfig.insecure_ssl : insecure ? "1" : "0"
        }
      }
    })
  );
}
async function updateWebhook(params) {
  requireToken("update_webhook");
  const parsedConfig = parseJsonParam(params.config, "config") || {};
  const insecure = parseBool(params.insecure_ssl);
  const config = __spreadValues({}, parsedConfig);
  if (params.url) config.url = params.url;
  if (params.content_type) config.content_type = params.content_type;
  if (params.secret) config.secret = params.secret;
  if (insecure !== void 0) config.insecure_ssl = insecure ? "1" : "0";
  const url = buildUrl(repoPath(params.owner, params.repo, `/hooks/${encodeURIComponent(String(params.hook_id))}`));
  return compactWebhook(
    await requestJson({
      method: "PATCH",
      url,
      body: {
        active: parseBool(params.active),
        events: splitCsv(params.events),
        add_events: splitCsv(params.add_events),
        remove_events: splitCsv(params.remove_events),
        config: Object.keys(config).length > 0 ? config : void 0
      }
    })
  );
}
async function deleteWebhook(params) {
  requireToken("delete_webhook");
  const url = buildUrl(repoPath(params.owner, params.repo, `/hooks/${encodeURIComponent(String(params.hook_id))}`));
  const result = await requestJson({ method: "DELETE", url });
  return { ok: true, hook_id: params.hook_id, result };
}
async function pingWebhook(params) {
  requireToken("ping_webhook");
  const url = buildUrl(repoPath(params.owner, params.repo, `/hooks/${encodeURIComponent(String(params.hook_id))}/pings`));
  const result = await requestJson({ method: "POST", url });
  return { ok: true, hook_id: params.hook_id, result };
}

// src/github/collaborators.ts
function compactCollaborator(item) {
  if (!item) return item;
  return {
    login: item.login,
    id: item.id,
    type: item.type,
    html_url: item.html_url,
    role_name: item.role_name,
    permissions: item.permissions
  };
}
async function listCollaborators(params) {
  var _a, _b;
  requireToken("list_collaborators");
  const url = buildUrl(repoPath(params.owner, params.repo, "/collaborators"), {
    affiliation: params.affiliation,
    permission: params.permission,
    page: (_a = params.page) != null ? _a : 1,
    per_page: (_b = params.per_page) != null ? _b : 30
  });
  const items = await requestJson({ method: "GET", url });
  return Array.isArray(items) ? items.map(compactCollaborator) : [];
}
async function addCollaborator(params) {
  var _a, _b;
  requireToken("add_collaborator");
  const url = buildUrl(
    repoPath(params.owner, params.repo, `/collaborators/${encodeURIComponent(params.username)}`)
  );
  const data = await requestJson({
    method: "PUT",
    url,
    body: {
      permission: params.permission
    }
  });
  return {
    username: params.username,
    permission: params.permission || "push",
    invited: Boolean(data && (data.id || data.html_url)),
    invitation: data && data.id ? {
      id: data.id,
      html_url: data.html_url,
      permissions: data.permissions,
      invitee: (_a = data.invitee) == null ? void 0 : _a.login,
      inviter: (_b = data.inviter) == null ? void 0 : _b.login
    } : data
  };
}
async function removeCollaborator(params) {
  requireToken("remove_collaborator");
  const url = buildUrl(
    repoPath(params.owner, params.repo, `/collaborators/${encodeURIComponent(params.username)}`)
  );
  const result = await requestJson({ method: "DELETE", url });
  return { ok: true, username: params.username, result };
}
async function getCollaboratorPermission(params) {
  var _a;
  requireToken("get_collaborator_permission");
  const url = buildUrl(
    repoPath(params.owner, params.repo, `/collaborators/${encodeURIComponent(params.username)}/permission`)
  );
  const data = await requestJson({ method: "GET", url });
  return {
    permission: data.permission,
    role_name: data.role_name,
    user: (_a = data.user) == null ? void 0 : _a.login
  };
}
async function checkCollaborator(params) {
  requireToken("check_collaborator");
  const url = buildUrl(
    repoPath(params.owner, params.repo, `/collaborators/${encodeURIComponent(params.username)}`)
  );
  try {
    const resp = await requestRaw({ method: "GET", url });
    return { username: params.username, is_collaborator: resp.statusCode === 204 || resp.statusCode === 200 };
  } catch (e) {
    const message = String(e && e.message ? e.message : e);
    if (message.includes("404")) {
      return { username: params.username, is_collaborator: false };
    }
    throw e;
  }
}

// src/github/labels.ts
function compactLabel(item) {
  if (!item) return item;
  return {
    id: item.id,
    name: item.name,
    color: item.color,
    description: item.description,
    default: item.default,
    url: item.url
  };
}
async function listLabels(params) {
  var _a, _b;
  const url = buildUrl(repoPath(params.owner, params.repo, "/labels"), {
    page: (_a = params.page) != null ? _a : 1,
    per_page: (_b = params.per_page) != null ? _b : 30
  });
  const items = await requestJson({ method: "GET", url });
  return Array.isArray(items) ? items.map(compactLabel) : [];
}
async function getLabel(params) {
  const url = buildUrl(repoPath(params.owner, params.repo, `/labels/${encodeURIComponent(params.name)}`));
  return compactLabel(await requestJson({ method: "GET", url }));
}
async function createLabel(params) {
  requireToken("create_label");
  const url = buildUrl(repoPath(params.owner, params.repo, "/labels"));
  return compactLabel(
    await requestJson({
      method: "POST",
      url,
      body: {
        name: params.name,
        color: String(params.color || "").replace(/^#/, ""),
        description: params.description
      }
    })
  );
}
async function updateLabel(params) {
  requireToken("update_label");
  const url = buildUrl(repoPath(params.owner, params.repo, `/labels/${encodeURIComponent(params.name)}`));
  return compactLabel(
    await requestJson({
      method: "PATCH",
      url,
      body: {
        new_name: params.new_name,
        color: params.color ? String(params.color).replace(/^#/, "") : void 0,
        description: params.description
      }
    })
  );
}
async function deleteLabel(params) {
  requireToken("delete_label");
  const url = buildUrl(repoPath(params.owner, params.repo, `/labels/${encodeURIComponent(params.name)}`));
  const result = await requestJson({ method: "DELETE", url });
  return { ok: true, name: params.name, result };
}

// src/github/milestones.ts
function compactMilestone(item) {
  var _a;
  if (!item) return item;
  return {
    id: item.id,
    number: item.number,
    title: item.title,
    description: item.description,
    state: item.state,
    html_url: item.html_url,
    open_issues: item.open_issues,
    closed_issues: item.closed_issues,
    due_on: item.due_on,
    created_at: item.created_at,
    updated_at: item.updated_at,
    closed_at: item.closed_at,
    creator: (_a = item.creator) == null ? void 0 : _a.login
  };
}
async function listMilestones(params) {
  var _a, _b, _c;
  const url = buildUrl(repoPath(params.owner, params.repo, "/milestones"), {
    state: (_a = params.state) != null ? _a : "open",
    sort: params.sort,
    direction: params.direction,
    page: (_b = params.page) != null ? _b : 1,
    per_page: (_c = params.per_page) != null ? _c : 30
  });
  const items = await requestJson({ method: "GET", url });
  return Array.isArray(items) ? items.map(compactMilestone) : [];
}
async function getMilestone(params) {
  const url = buildUrl(
    repoPath(params.owner, params.repo, `/milestones/${encodeURIComponent(String(params.milestone_number))}`)
  );
  return compactMilestone(await requestJson({ method: "GET", url }));
}
async function createMilestone(params) {
  requireToken("create_milestone");
  const url = buildUrl(repoPath(params.owner, params.repo, "/milestones"));
  return compactMilestone(
    await requestJson({
      method: "POST",
      url,
      body: {
        title: params.title,
        state: params.state,
        description: params.description,
        due_on: params.due_on
      }
    })
  );
}
async function updateMilestone(params) {
  requireToken("update_milestone");
  const url = buildUrl(
    repoPath(params.owner, params.repo, `/milestones/${encodeURIComponent(String(params.milestone_number))}`)
  );
  return compactMilestone(
    await requestJson({
      method: "PATCH",
      url,
      body: {
        title: params.title,
        state: params.state,
        description: params.description,
        due_on: params.due_on
      }
    })
  );
}
async function deleteMilestone(params) {
  requireToken("delete_milestone");
  const url = buildUrl(
    repoPath(params.owner, params.repo, `/milestones/${encodeURIComponent(String(params.milestone_number))}`)
  );
  const result = await requestJson({ method: "DELETE", url });
  return { ok: true, milestone_number: params.milestone_number, result };
}

// src/github/protection.ts
function compactProtection(item) {
  var _a, _b, _c, _d, _e, _f, _g, _h, _i, _j, _k, _l, _m, _n, _o, _p;
  if (!item) return item;
  return {
    url: item.url,
    required_status_checks: item.required_status_checks ? {
      strict: item.required_status_checks.strict,
      contexts: item.required_status_checks.contexts,
      checks: item.required_status_checks.checks
    } : item.required_status_checks,
    enforce_admins: (_b = (_a = item.enforce_admins) == null ? void 0 : _a.enabled) != null ? _b : item.enforce_admins,
    required_pull_request_reviews: item.required_pull_request_reviews ? {
      dismiss_stale_reviews: item.required_pull_request_reviews.dismiss_stale_reviews,
      require_code_owner_reviews: item.required_pull_request_reviews.require_code_owner_reviews,
      required_approving_review_count: item.required_pull_request_reviews.required_approving_review_count,
      require_last_push_approval: item.required_pull_request_reviews.require_last_push_approval
    } : item.required_pull_request_reviews,
    restrictions: item.restrictions ? {
      users: Array.isArray(item.restrictions.users) ? item.restrictions.users.map((u) => u.login || u) : [],
      teams: Array.isArray(item.restrictions.teams) ? item.restrictions.teams.map((t) => t.slug || t.name || t) : [],
      apps: Array.isArray(item.restrictions.apps) ? item.restrictions.apps.map((a) => a.slug || a.name || a) : []
    } : item.restrictions,
    required_linear_history: (_d = (_c = item.required_linear_history) == null ? void 0 : _c.enabled) != null ? _d : item.required_linear_history,
    allow_force_pushes: (_f = (_e = item.allow_force_pushes) == null ? void 0 : _e.enabled) != null ? _f : item.allow_force_pushes,
    allow_deletions: (_h = (_g = item.allow_deletions) == null ? void 0 : _g.enabled) != null ? _h : item.allow_deletions,
    block_creations: (_j = (_i = item.block_creations) == null ? void 0 : _i.enabled) != null ? _j : item.block_creations,
    required_conversation_resolution: (_l = (_k = item.required_conversation_resolution) == null ? void 0 : _k.enabled) != null ? _l : item.required_conversation_resolution,
    lock_branch: (_n = (_m = item.lock_branch) == null ? void 0 : _m.enabled) != null ? _n : item.lock_branch,
    allow_fork_syncing: (_p = (_o = item.allow_fork_syncing) == null ? void 0 : _o.enabled) != null ? _p : item.allow_fork_syncing
  };
}
async function getBranchProtection(params) {
  requireToken("get_branch_protection");
  const url = buildUrl(repoPath(params.owner, params.repo, `/branches/${encodeRef(params.branch)}/protection`));
  return compactProtection(await requestJson({ method: "GET", url }));
}
async function updateBranchProtection(params) {
  requireToken("update_branch_protection");
  const protection = parseJsonParam(params.protection, "protection");
  if (!protection || typeof protection !== "object") {
    throw new Error("protection must be a JSON object matching PUT /branches/{branch}/protection");
  }
  const url = buildUrl(repoPath(params.owner, params.repo, `/branches/${encodeRef(params.branch)}/protection`));
  return compactProtection(
    await requestJson({
      method: "PUT",
      url,
      body: protection
    })
  );
}
async function deleteBranchProtection(params) {
  requireToken("delete_branch_protection");
  const url = buildUrl(repoPath(params.owner, params.repo, `/branches/${encodeRef(params.branch)}/protection`));
  const result = await requestJson({ method: "DELETE", url });
  return { ok: true, branch: params.branch, result };
}

// src/github/stats.ts
async function requestStats(url, kind) {
  const resp = await requestRaw({ method: "GET", url });
  if (resp.statusCode === 202) {
    return {
      status: "computing",
      retry: true,
      kind,
      statusCode: 202,
      message: "GitHub is computing repository statistics. Retry shortly."
    };
  }
  if (resp.statusCode === 204) {
    return {
      status: "empty",
      kind,
      statusCode: 204,
      data: []
    };
  }
  const text = String(resp.content || "").trim();
  if (!text) {
    return { status: "empty", kind, statusCode: resp.statusCode, data: [] };
  }
  try {
    return {
      status: "ready",
      kind,
      statusCode: resp.statusCode,
      data: JSON.parse(text)
    };
  } catch (e) {
    throw new Error(`Failed to parse GitHub stats JSON (${resp.statusCode}): ${text.slice(0, 500)}`);
  }
}
async function getContributorsStats(params) {
  const url = buildUrl(repoPath(params.owner, params.repo, "/stats/contributors"));
  const result = await requestStats(url, "contributors");
  if (result.status !== "ready" || !Array.isArray(result.data)) return result;
  return __spreadProps(__spreadValues({}, result), {
    data: result.data.map((item) => {
      var _a;
      return {
        author: (_a = item.author) == null ? void 0 : _a.login,
        total: item.total,
        weeks: item.weeks
      };
    })
  });
}
async function getCommitActivity(params) {
  const url = buildUrl(repoPath(params.owner, params.repo, "/stats/commit_activity"));
  return requestStats(url, "commit_activity");
}
async function getCodeFrequency(params) {
  const url = buildUrl(repoPath(params.owner, params.repo, "/stats/code_frequency"));
  return requestStats(url, "code_frequency");
}

// src/utils/wrap.ts
async function wrap(func, params, successMessage, failMessage) {
  try {
    const data = await func(params);
    const result = { success: true, message: successMessage, data };
    complete(result);
  } catch (error) {
    const result = {
      success: false,
      message: `${failMessage}: ${String(error && error.message ? error.message : error)}`,
      error_stack: String(error && error.stack ? error.stack : "")
    };
    complete(result);
  }
}

// src/index.ts
var toolImpl = {
  search_repositories: (p) => wrap(searchRepositories, p, "\u641C\u7D22\u4ED3\u5E93\u6210\u529F", "\u641C\u7D22\u4ED3\u5E93\u5931\u8D25"),
  get_repository: (p) => wrap(getRepository, p, "\u83B7\u53D6\u4ED3\u5E93\u6210\u529F", "\u83B7\u53D6\u4ED3\u5E93\u5931\u8D25"),
  list_issues: (p) => wrap(listIssues, p, "\u5217\u51FA Issues \u6210\u529F", "\u5217\u51FA Issues \u5931\u8D25"),
  get_issue: (p) => wrap(getIssue, p, "\u83B7\u53D6 Issue \u6210\u529F", "\u83B7\u53D6 Issue \u5931\u8D25"),
  create_issue: (p) => wrap(createIssue, p, "\u521B\u5EFA Issue \u6210\u529F", "\u521B\u5EFA Issue \u5931\u8D25"),
  update_issue: (p) => wrap(updateIssue, p, "\u66F4\u65B0 Issue \u6210\u529F", "\u66F4\u65B0 Issue \u5931\u8D25"),
  comment_issue: (p) => wrap(commentIssue, p, "\u8BC4\u8BBA\u6210\u529F", "\u8BC4\u8BBA\u5931\u8D25"),
  list_issue_comments: (p) => wrap(listIssueComments, p, "\u5217\u51FA\u8BC4\u8BBA\u6210\u529F", "\u5217\u51FA\u8BC4\u8BBA\u5931\u8D25"),
  list_pull_requests: (p) => wrap(listPullRequests, p, "\u5217\u51FA PR \u6210\u529F", "\u5217\u51FA PR \u5931\u8D25"),
  create_pull_request: (p) => wrap(createPullRequest, p, "\u521B\u5EFA PR \u6210\u529F", "\u521B\u5EFA PR \u5931\u8D25"),
  get_pull_request: (p) => wrap(getPullRequest, p, "\u83B7\u53D6 PR \u6210\u529F", "\u83B7\u53D6 PR \u5931\u8D25"),
  update_pull_request: (p) => wrap(updatePullRequest, p, "\u66F4\u65B0 PR \u6210\u529F", "\u66F4\u65B0 PR \u5931\u8D25"),
  merge_pull_request: (p) => wrap(mergePullRequest, p, "\u5408\u5E76 PR \u6210\u529F", "\u5408\u5E76 PR \u5931\u8D25"),
  list_pull_request_files: (p) => wrap(listPullRequestFiles, p, "\u83B7\u53D6 PR \u6587\u4EF6\u6210\u529F", "\u83B7\u53D6 PR \u6587\u4EF6\u5931\u8D25"),
  get_pull_request_diff: (p) => wrap(getPullRequestDiff, p, "\u83B7\u53D6 PR diff \u6210\u529F", "\u83B7\u53D6 PR diff \u5931\u8D25"),
  list_pull_request_commits: (p) => wrap(listPullRequestCommits, p, "\u83B7\u53D6 PR \u63D0\u4EA4\u6210\u529F", "\u83B7\u53D6 PR \u63D0\u4EA4\u5931\u8D25"),
  list_pull_request_reviews: (p) => wrap(listPullRequestReviews, p, "\u83B7\u53D6 PR review \u6210\u529F", "\u83B7\u53D6 PR review \u5931\u8D25"),
  list_review_comments: (p) => wrap(listReviewComments, p, "\u83B7\u53D6\u884C\u5185\u8BC4\u8BBA\u6210\u529F", "\u83B7\u53D6\u884C\u5185\u8BC4\u8BBA\u5931\u8D25"),
  create_review: (p) => wrap(createReview, p, "\u63D0\u4EA4 review \u6210\u529F", "\u63D0\u4EA4 review \u5931\u8D25"),
  reply_review_comment: (p) => wrap(replyReviewComment, p, "\u56DE\u590D\u8BC4\u8BBA\u6210\u529F", "\u56DE\u590D\u8BC4\u8BBA\u5931\u8D25"),
  request_reviewers: (p) => wrap(requestReviewers, p, "\u8BF7\u6C42 reviewer \u6210\u529F", "\u8BF7\u6C42 reviewer \u5931\u8D25"),
  get_file_content: (p) => wrap(getFileContent, p, "\u83B7\u53D6\u6587\u4EF6\u6210\u529F", "\u83B7\u53D6\u6587\u4EF6\u5931\u8D25"),
  create_or_update_file: (p) => wrap(createOrUpdateFile, p, "\u5199\u5165\u6587\u4EF6\u6210\u529F", "\u5199\u5165\u6587\u4EF6\u5931\u8D25"),
  delete_file: (p) => wrap(deleteFile, p, "\u5220\u9664\u6587\u4EF6\u6210\u529F", "\u5220\u9664\u6587\u4EF6\u5931\u8D25"),
  create_branch: (p) => wrap(createBranch, p, "\u521B\u5EFA\u5206\u652F\u6210\u529F", "\u521B\u5EFA\u5206\u652F\u5931\u8D25"),
  delete_branch: (p) => wrap(deleteBranch, p, "\u5220\u9664\u5206\u652F\u6210\u529F", "\u5220\u9664\u5206\u652F\u5931\u8D25"),
  list_branches: (p) => wrap(listBranches, p, "\u5217\u51FA\u5206\u652F\u6210\u529F", "\u5217\u51FA\u5206\u652F\u5931\u8D25"),
  list_commits: (p) => wrap(listCommits, p, "\u5217\u51FA\u63D0\u4EA4\u6210\u529F", "\u5217\u51FA\u63D0\u4EA4\u5931\u8D25"),
  get_commit: (p) => wrap(getCommit, p, "\u83B7\u53D6\u63D0\u4EA4\u6210\u529F", "\u83B7\u53D6\u63D0\u4EA4\u5931\u8D25"),
  compare_refs: (p) => wrap(compareRefs, p, "\u6BD4\u8F83 refs \u6210\u529F", "\u6BD4\u8F83 refs \u5931\u8D25"),
  get_compare_diff: (p) => wrap(getCompareDiff, p, "\u83B7\u53D6 diff \u6210\u529F", "\u83B7\u53D6 diff \u5931\u8D25"),
  list_workflows: (p) => wrap(listWorkflows, p, "\u5217\u51FA workflow \u6210\u529F", "\u5217\u51FA workflow \u5931\u8D25"),
  list_workflow_runs: (p) => wrap(listWorkflowRuns, p, "\u83B7\u53D6 workflow run \u6210\u529F", "\u83B7\u53D6 workflow run \u5931\u8D25"),
  get_workflow_run: (p) => wrap(getWorkflowRun, p, "\u83B7\u53D6 workflow run \u6210\u529F", "\u83B7\u53D6 workflow run \u5931\u8D25"),
  trigger_workflow: (p) => wrap(triggerWorkflow, p, "\u89E6\u53D1 workflow \u6210\u529F", "\u89E6\u53D1 workflow \u5931\u8D25"),
  get_workflow_jobs: (p) => wrap(getWorkflowJobs, p, "\u83B7\u53D6 workflow jobs \u6210\u529F", "\u83B7\u53D6 workflow jobs \u5931\u8D25"),
  rerun_workflow_run: (p) => wrap(rerunWorkflowRun, p, "\u91CD\u65B0\u8FD0\u884C workflow \u6210\u529F", "\u91CD\u65B0\u8FD0\u884C workflow \u5931\u8D25"),
  cancel_workflow_run: (p) => wrap(cancelWorkflowRun, p, "\u53D6\u6D88 workflow \u6210\u529F", "\u53D6\u6D88 workflow \u5931\u8D25"),
  list_check_runs: (p) => wrap(listCheckRuns, p, "\u5217\u51FA check runs \u6210\u529F", "\u5217\u51FA check runs \u5931\u8D25"),
  search_code: (p) => wrap(searchCode, p, "\u641C\u7D22\u4EE3\u7801\u6210\u529F", "\u641C\u7D22\u4EE3\u7801\u5931\u8D25"),
  search_issues: (p) => wrap(searchIssues, p, "\u641C\u7D22 Issues \u6210\u529F", "\u641C\u7D22 Issues \u5931\u8D25"),
  get_authenticated_user: (p) => wrap(getAuthenticatedUser, p, "\u83B7\u53D6\u7528\u6237\u6210\u529F", "\u83B7\u53D6\u7528\u6237\u5931\u8D25"),
  get_rate_limit: (p) => wrap(getRateLimit, p, "\u83B7\u53D6 rate limit \u6210\u529F", "\u83B7\u53D6 rate limit \u5931\u8D25"),
  fork_repository: (p) => wrap(forkRepository, p, "fork \u4ED3\u5E93\u6210\u529F", "fork \u4ED3\u5E93\u5931\u8D25"),
  sync_fork: (p) => wrap(syncFork, p, "\u540C\u6B65 fork \u6210\u529F", "\u540C\u6B65 fork \u5931\u8D25"),
  patch_file_in_repo: (p) => wrap(patchFileInRepo, p, "patch \u6587\u4EF6\u6210\u529F", "patch \u6587\u4EF6\u5931\u8D25"),
  apply_local_replace: (p) => wrap(applyLocalReplace, p, "\u672C\u5730\u66FF\u6362\u6210\u529F", "\u672C\u5730\u66FF\u6362\u5931\u8D25"),
  apply_local_delete: (p) => wrap(applyLocalDelete, p, "\u672C\u5730\u5220\u9664\u6210\u529F", "\u672C\u5730\u5220\u9664\u5931\u8D25"),
  overwrite_local_file: (p) => wrap(overwriteLocalFile, p, "\u8986\u76D6\u6587\u4EF6\u6210\u529F", "\u8986\u76D6\u6587\u4EF6\u5931\u8D25"),
  terminal_exec: (p) => wrap(terminalExec, p, "\u7EC8\u7AEF\u6267\u884C\u6210\u529F", "\u7EC8\u7AEF\u6267\u884C\u5931\u8D25"),
  list_releases: (p) => wrap(listReleases, p, "\u5217\u51FA Releases \u6210\u529F", "\u5217\u51FA Releases \u5931\u8D25"),
  get_release: (p) => wrap(getRelease, p, "\u83B7\u53D6 Release \u6210\u529F", "\u83B7\u53D6 Release \u5931\u8D25"),
  get_latest_release: (p) => wrap(getLatestRelease, p, "\u83B7\u53D6\u6700\u65B0 Release \u6210\u529F", "\u83B7\u53D6\u6700\u65B0 Release \u5931\u8D25"),
  get_release_by_tag: (p) => wrap(getReleaseByTag, p, "\u6309 tag \u83B7\u53D6 Release \u6210\u529F", "\u6309 tag \u83B7\u53D6 Release \u5931\u8D25"),
  create_release: (p) => wrap(createRelease, p, "\u521B\u5EFA Release \u6210\u529F", "\u521B\u5EFA Release \u5931\u8D25"),
  update_release: (p) => wrap(updateRelease, p, "\u66F4\u65B0 Release \u6210\u529F", "\u66F4\u65B0 Release \u5931\u8D25"),
  delete_release: (p) => wrap(deleteRelease, p, "\u5220\u9664 Release \u6210\u529F", "\u5220\u9664 Release \u5931\u8D25"),
  upload_release_asset: (p) => wrap(uploadReleaseAsset, p, "\u4E0A\u4F20 Release \u8D44\u4EA7\u6210\u529F", "\u4E0A\u4F20 Release \u8D44\u4EA7\u5931\u8D25"),
  delete_release_asset: (p) => wrap(deleteReleaseAsset, p, "\u5220\u9664 Release \u8D44\u4EA7\u6210\u529F", "\u5220\u9664 Release \u8D44\u4EA7\u5931\u8D25"),
  list_webhooks: (p) => wrap(listWebhooks, p, "\u5217\u51FA Webhooks \u6210\u529F", "\u5217\u51FA Webhooks \u5931\u8D25"),
  get_webhook: (p) => wrap(getWebhook, p, "\u83B7\u53D6 Webhook \u6210\u529F", "\u83B7\u53D6 Webhook \u5931\u8D25"),
  create_webhook: (p) => wrap(createWebhook, p, "\u521B\u5EFA Webhook \u6210\u529F", "\u521B\u5EFA Webhook \u5931\u8D25"),
  update_webhook: (p) => wrap(updateWebhook, p, "\u66F4\u65B0 Webhook \u6210\u529F", "\u66F4\u65B0 Webhook \u5931\u8D25"),
  delete_webhook: (p) => wrap(deleteWebhook, p, "\u5220\u9664 Webhook \u6210\u529F", "\u5220\u9664 Webhook \u5931\u8D25"),
  ping_webhook: (p) => wrap(pingWebhook, p, "ping Webhook \u6210\u529F", "ping Webhook \u5931\u8D25"),
  list_collaborators: (p) => wrap(listCollaborators, p, "\u5217\u51FA\u534F\u4F5C\u8005\u6210\u529F", "\u5217\u51FA\u534F\u4F5C\u8005\u5931\u8D25"),
  check_collaborator: (p) => wrap(checkCollaborator, p, "\u68C0\u67E5\u534F\u4F5C\u8005\u6210\u529F", "\u68C0\u67E5\u534F\u4F5C\u8005\u5931\u8D25"),
  add_collaborator: (p) => wrap(addCollaborator, p, "\u6DFB\u52A0\u534F\u4F5C\u8005\u6210\u529F", "\u6DFB\u52A0\u534F\u4F5C\u8005\u5931\u8D25"),
  remove_collaborator: (p) => wrap(removeCollaborator, p, "\u79FB\u9664\u534F\u4F5C\u8005\u6210\u529F", "\u79FB\u9664\u534F\u4F5C\u8005\u5931\u8D25"),
  get_collaborator_permission: (p) => wrap(getCollaboratorPermission, p, "\u83B7\u53D6\u534F\u4F5C\u8005\u6743\u9650\u6210\u529F", "\u83B7\u53D6\u534F\u4F5C\u8005\u6743\u9650\u5931\u8D25"),
  list_labels: (p) => wrap(listLabels, p, "\u5217\u51FA Labels \u6210\u529F", "\u5217\u51FA Labels \u5931\u8D25"),
  get_label: (p) => wrap(getLabel, p, "\u83B7\u53D6 Label \u6210\u529F", "\u83B7\u53D6 Label \u5931\u8D25"),
  create_label: (p) => wrap(createLabel, p, "\u521B\u5EFA Label \u6210\u529F", "\u521B\u5EFA Label \u5931\u8D25"),
  update_label: (p) => wrap(updateLabel, p, "\u66F4\u65B0 Label \u6210\u529F", "\u66F4\u65B0 Label \u5931\u8D25"),
  delete_label: (p) => wrap(deleteLabel, p, "\u5220\u9664 Label \u6210\u529F", "\u5220\u9664 Label \u5931\u8D25"),
  list_milestones: (p) => wrap(listMilestones, p, "\u5217\u51FA Milestones \u6210\u529F", "\u5217\u51FA Milestones \u5931\u8D25"),
  get_milestone: (p) => wrap(getMilestone, p, "\u83B7\u53D6 Milestone \u6210\u529F", "\u83B7\u53D6 Milestone \u5931\u8D25"),
  create_milestone: (p) => wrap(createMilestone, p, "\u521B\u5EFA Milestone \u6210\u529F", "\u521B\u5EFA Milestone \u5931\u8D25"),
  update_milestone: (p) => wrap(updateMilestone, p, "\u66F4\u65B0 Milestone \u6210\u529F", "\u66F4\u65B0 Milestone \u5931\u8D25"),
  delete_milestone: (p) => wrap(deleteMilestone, p, "\u5220\u9664 Milestone \u6210\u529F", "\u5220\u9664 Milestone \u5931\u8D25"),
  get_branch_protection: (p) => wrap(getBranchProtection, p, "\u83B7\u53D6\u5206\u652F\u4FDD\u62A4\u6210\u529F", "\u83B7\u53D6\u5206\u652F\u4FDD\u62A4\u5931\u8D25"),
  update_branch_protection: (p) => wrap(updateBranchProtection, p, "\u66F4\u65B0\u5206\u652F\u4FDD\u62A4\u6210\u529F", "\u66F4\u65B0\u5206\u652F\u4FDD\u62A4\u5931\u8D25"),
  delete_branch_protection: (p) => wrap(deleteBranchProtection, p, "\u5220\u9664\u5206\u652F\u4FDD\u62A4\u6210\u529F", "\u5220\u9664\u5206\u652F\u4FDD\u62A4\u5931\u8D25"),
  get_contributors_stats: (p) => wrap(getContributorsStats, p, "\u83B7\u53D6\u8D21\u732E\u8005\u7EDF\u8BA1\u6210\u529F", "\u83B7\u53D6\u8D21\u732E\u8005\u7EDF\u8BA1\u5931\u8D25"),
  get_commit_activity: (p) => wrap(getCommitActivity, p, "\u83B7\u53D6\u63D0\u4EA4\u6D3B\u52A8\u6210\u529F", "\u83B7\u53D6\u63D0\u4EA4\u6D3B\u52A8\u5931\u8D25"),
  get_code_frequency: (p) => wrap(getCodeFrequency, p, "\u83B7\u53D6\u4EE3\u7801\u9891\u7387\u6210\u529F", "\u83B7\u53D6\u4EE3\u7801\u9891\u7387\u5931\u8D25")
};
async function main(params) {
  const tool = params == null ? void 0 : params.tool;
  if (!tool) {
    return { ok: true, message: "GitHub API package loaded", tools: Object.keys(toolImpl) };
  }
  const fn = toolImpl[tool];
  if (!fn) {
    return { success: false, message: `Unknown tool: ${tool}`, available: Object.keys(toolImpl) };
  }
  return fn(params);
}
var search_repositories = toolImpl.search_repositories;
var get_repository = toolImpl.get_repository;
var list_issues = toolImpl.list_issues;
var get_issue = toolImpl.get_issue;
var create_issue = toolImpl.create_issue;
var update_issue = toolImpl.update_issue;
var comment_issue = toolImpl.comment_issue;
var list_issue_comments = toolImpl.list_issue_comments;
var list_pull_requests = toolImpl.list_pull_requests;
var create_pull_request = toolImpl.create_pull_request;
var get_pull_request = toolImpl.get_pull_request;
var update_pull_request = toolImpl.update_pull_request;
var merge_pull_request = toolImpl.merge_pull_request;
var list_pull_request_files = toolImpl.list_pull_request_files;
var get_pull_request_diff = toolImpl.get_pull_request_diff;
var list_pull_request_commits = toolImpl.list_pull_request_commits;
var list_pull_request_reviews = toolImpl.list_pull_request_reviews;
var list_review_comments = toolImpl.list_review_comments;
var create_review = toolImpl.create_review;
var reply_review_comment = toolImpl.reply_review_comment;
var request_reviewers = toolImpl.request_reviewers;
var get_file_content = toolImpl.get_file_content;
var create_or_update_file = toolImpl.create_or_update_file;
var delete_file = toolImpl.delete_file;
var create_branch = toolImpl.create_branch;
var delete_branch = toolImpl.delete_branch;
var list_branches = toolImpl.list_branches;
var list_commits = toolImpl.list_commits;
var get_commit = toolImpl.get_commit;
var compare_refs = toolImpl.compare_refs;
var get_compare_diff = toolImpl.get_compare_diff;
var list_workflows = toolImpl.list_workflows;
var list_workflow_runs = toolImpl.list_workflow_runs;
var get_workflow_run = toolImpl.get_workflow_run;
var trigger_workflow = toolImpl.trigger_workflow;
var get_workflow_jobs = toolImpl.get_workflow_jobs;
var rerun_workflow_run = toolImpl.rerun_workflow_run;
var cancel_workflow_run = toolImpl.cancel_workflow_run;
var list_check_runs = toolImpl.list_check_runs;
var search_code = toolImpl.search_code;
var search_issues = toolImpl.search_issues;
var get_authenticated_user = toolImpl.get_authenticated_user;
var get_rate_limit = toolImpl.get_rate_limit;
var fork_repository = toolImpl.fork_repository;
var sync_fork = toolImpl.sync_fork;
var patch_file_in_repo = toolImpl.patch_file_in_repo;
var apply_local_replace = toolImpl.apply_local_replace;
var apply_local_delete = toolImpl.apply_local_delete;
var overwrite_local_file = toolImpl.overwrite_local_file;
var terminal_exec = toolImpl.terminal_exec;
var list_releases = toolImpl.list_releases;
var get_release = toolImpl.get_release;
var get_latest_release = toolImpl.get_latest_release;
var get_release_by_tag = toolImpl.get_release_by_tag;
var create_release = toolImpl.create_release;
var update_release = toolImpl.update_release;
var delete_release = toolImpl.delete_release;
var upload_release_asset = toolImpl.upload_release_asset;
var delete_release_asset = toolImpl.delete_release_asset;
var list_webhooks = toolImpl.list_webhooks;
var get_webhook = toolImpl.get_webhook;
var create_webhook = toolImpl.create_webhook;
var update_webhook = toolImpl.update_webhook;
var delete_webhook = toolImpl.delete_webhook;
var ping_webhook = toolImpl.ping_webhook;
var list_collaborators = toolImpl.list_collaborators;
var check_collaborator = toolImpl.check_collaborator;
var add_collaborator = toolImpl.add_collaborator;
var remove_collaborator = toolImpl.remove_collaborator;
var get_collaborator_permission = toolImpl.get_collaborator_permission;
var list_labels = toolImpl.list_labels;
var get_label = toolImpl.get_label;
var create_label = toolImpl.create_label;
var update_label = toolImpl.update_label;
var delete_label = toolImpl.delete_label;
var list_milestones = toolImpl.list_milestones;
var get_milestone = toolImpl.get_milestone;
var create_milestone = toolImpl.create_milestone;
var update_milestone = toolImpl.update_milestone;
var delete_milestone = toolImpl.delete_milestone;
var get_branch_protection = toolImpl.get_branch_protection;
var update_branch_protection = toolImpl.update_branch_protection;
var delete_branch_protection = toolImpl.delete_branch_protection;
var get_contributors_stats = toolImpl.get_contributors_stats;
var get_commit_activity = toolImpl.get_commit_activity;
var get_code_frequency = toolImpl.get_code_frequency;
