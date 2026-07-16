import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('../views/LoginView.vue')
    },
    {
      path: '/',
      name: 'Dashboard',
      component: () => import('../views/DashboardView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/chat',
      name: 'Chat',
      component: () => import('../views/ChatView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/knowledge-bases',
      name: 'KnowledgeBases',
      component: () => import('../views/KnowledgeBaseView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/documents',
      name: 'Documents',
      component: () => import('../views/DocumentView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/analysis',
      name: 'Analysis',
      component: () => import('../views/AnalysisView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/citations',
      name: 'Citations',
      component: () => import('../views/CitationView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/academic',
      name: 'Academic',
      component: () => import('../views/AcademicGraphView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/knowledge-graph',
      name: 'KnowledgeGraph',
      component: () => import('../views/KnowledgeGraphView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/notes',
      name: 'Notes',
      component: () => import('../views/NotesView.vue'),
      meta: { requiresAuth: true }
    }
  ]
})

function safeRedirectTarget(redirect: unknown): string {
  const target = Array.isArray(redirect) ? redirect[0] : redirect
  if (typeof target !== 'string') return '/'
  if (!target.startsWith('/') || target.startsWith('//') || target.startsWith('/login')) return '/'
  return target
}

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('accessToken')
  if (to.meta.requiresAuth && !token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else if (to.path === '/login' && token) {
    next(safeRedirectTarget(to.query.redirect))
  } else {
    next()
  }
})

export default router
