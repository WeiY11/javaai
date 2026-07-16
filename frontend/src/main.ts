import { createApp } from 'vue'
import { createPinia } from 'pinia'
import {
  ElButton,
  ElCollapse,
  ElCollapseItem,
  ElColorPicker,
  ElDialog,
  ElDivider,
  ElDrawer,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElLoading,
  ElOption,
  ElProgress,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElSlider,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
  ElUpload
} from 'element-plus'
import 'element-plus/dist/index.css'
import router from './router'
import App from './App.vue'
import './style.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElButton)
app.use(ElCollapse)
app.use(ElCollapseItem)
app.use(ElColorPicker)
app.use(ElDialog)
app.use(ElDivider)
app.use(ElDrawer)
app.use(ElEmpty)
app.use(ElForm)
app.use(ElFormItem)
app.use(ElIcon)
app.use(ElInput)
app.use(ElInputNumber)
app.use(ElLoading)
app.use(ElOption)
app.use(ElProgress)
app.use(ElRadioButton)
app.use(ElRadioGroup)
app.use(ElSelect)
app.use(ElSlider)
app.use(ElSwitch)
app.use(ElTable)
app.use(ElTableColumn)
app.use(ElTag)
app.use(ElUpload)
app.mount('#app')
