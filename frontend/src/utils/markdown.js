import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/core'
import markdownItTaskLists from 'markdown-it-task-lists'
import { full as markdownItEmoji } from 'markdown-it-emoji'
import markdownItContainer from 'markdown-it-container'

// 按需注册常用语言（减小打包体积）
import javascript from 'highlight.js/lib/languages/javascript'
import python from 'highlight.js/lib/languages/python'
import java from 'highlight.js/lib/languages/java'
import sql from 'highlight.js/lib/languages/sql'
import json from 'highlight.js/lib/languages/json'
import xml from 'highlight.js/lib/languages/xml'
import bash from 'highlight.js/lib/languages/bash'
import css from 'highlight.js/lib/languages/css'

hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('js', javascript)
hljs.registerLanguage('python', python)
hljs.registerLanguage('java', java)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('json', json)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('shell', bash)
hljs.registerLanguage('css', css)

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  highlight(str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre class="hljs-code-block"><code class="hljs language-${lang}">${
          hljs.highlight(str, { language: lang, ignoreIllegals: true }).value
        }</code></pre>`
      } catch (_) {}
    }
    return `<pre class="hljs-code-block"><code class="hljs">${md.utils.escapeHtml(str)}</code></pre>`
  }
})

// 任务列表
md.use(markdownItTaskLists, { enabled: true, label: true, labelAfter: true })

// 表情符号
md.use(markdownItEmoji)

// 自定义容器：::: warning / ::: tip / ::: danger
function makeContainer(name, icon, cssClass) {
  md.use(markdownItContainer, name, {
    validate: function (params) { return params.trim().split(' ', 1)[0] === name },
    render: function (tokens, idx) {
      if (tokens[idx].nesting === 1) {
        return `<div class="md-container ${cssClass}"><div class="md-container-title">${icon}</div>\n`
      }
      return '</div>\n'
    }
  })
}
makeContainer('warning', '⚠️ 注意', 'md-container-warning')
makeContainer('tip', '💡 提示', 'md-container-tip')
makeContainer('danger', '🚫 警告', 'md-container-danger')

// 表格响应式包装
const defaultTableOpen = md.renderer.rules.table_open || function (tokens, idx, options, env, self) {
  return self.renderToken(tokens, idx, options)
}
md.renderer.rules.table_open = function (tokens, idx, options, env, self) {
  return '<div class="table-responsive-wrapper">' + defaultTableOpen(tokens, idx, options, env, self)
}
md.renderer.rules.table_close = function (tokens, idx, options, env, self) {
  return (self.renderToken(tokens, idx, options) || '</table>') + '</div>'
}

export function renderMarkdown(content) {
  if (!content) return ''
  return md.render(content)
}
