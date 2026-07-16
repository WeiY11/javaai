import { GraphChart } from 'echarts/charts'
import { LegendComponent, TooltipComponent } from 'echarts/components'
import { init, use, type ECharts } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'

use([GraphChart, LegendComponent, TooltipComponent, CanvasRenderer])

export type { ECharts }

export function initGraphChart(element: HTMLElement): ECharts {
  return init(element)
}
