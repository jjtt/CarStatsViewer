package com.ixam97.carStatsViewer

import android.R
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class PlotView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    private val textSize = 26f

    var xMargin: Int = 100
        set(value) {
            if (value > 0) {
                field = value
                invalidate()
            }
        }

    var xLineCount: Int = 6
        set(value) {
            if (value > 1) {
                field = value
                invalidate()
            }
        }

    var yMargin: Int = 60
        set(value) {
            if (value > 0) {
                field = value
                invalidate()
            }
        }

    var yLineCount: Int = 5
        set(value) {
            if (value > 1) {
                field = value
                invalidate()
            }
        }

    var displayItemCount: Int? = null
        set(value) {
            if (value == null || value > 0) {
                for (line in plotLines) {
                    line.displayItemCount = value
                }
                field = value
                invalidate()
            }
        }

    private val plotLines = ArrayList<PlotLine>()
    private val plotPaint = ArrayList<PlotPaint>()

    private lateinit var labelPaint: Paint
    private lateinit var labelLinePaint: Paint
    private lateinit var baseLinePaint: Paint

    init {
        setupPaint()
    }

    // Setup paint with color and stroke styles
    private fun setupPaint() {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.colorControlActivated, typedValue, true)

        val basePaint = PlotPaint.basePaint(textSize)

        labelLinePaint = Paint(basePaint)
        labelLinePaint.color = Color.DKGRAY

        labelPaint = Paint(labelLinePaint)
        labelPaint.style = Paint.Style.FILL

        baseLinePaint = Paint(labelLinePaint)
        baseLinePaint.color = Color.LTGRAY

        val plotColors = listOf(
            null,
            Color.GREEN,
            Color.CYAN,
            Color.BLUE,
            Color.RED
        )

        for (color in plotColors) {
            plotPaint.add(PlotPaint.byColor(color ?: typedValue.data, textSize))
        }
    }

    fun reset() {
        for (item in plotLines) {
            item.reset()
        }
        invalidate()
    }

    fun addPlotLine(plotLine: PlotLine) {
        if (plotLine.plotPaint == null) {
            plotLine.plotPaint = plotPaint[plotLines.size]
        }
        plotLines.add(plotLine)
        invalidate()
    }

    fun removePlotLine(plotLine: PlotLine?) {
        plotLines.remove(plotLine)
        invalidate()
    }

    fun removeAllPlotLine() {
        plotLines.clear()
        invalidate()
    }

    private fun x(index: Float?, max: Float, maxX: Float): Float? {
        return x(PlotLineItem.cord(index, 0f, max), maxX)
    }

    private fun x(value: Float?, maxX: Float): Float? {
        return when (value) {
            null -> null
            else -> xMargin + (maxX - (2 * xMargin)) * value
        }
    }

    private fun y(line: PlotLine, maxY: Float): Float? {
        return y(line.highlight(), line, maxY)
    }

    private fun y(index: Float?, max: Float, maxY: Float): Float? {
        return y(PlotLineItem.cord(index, 0f, max), maxY)
    }

    private fun y(index: Float?, line: PlotLine, maxY: Float): Float? {
        return y(line.y(index), maxY)
    }

    private fun y(value: Float?, maxY: Float): Float? {
        return when (value) {
            null -> null
            else -> {
                val px = maxY - (2 * yMargin)
                yMargin + abs(px - px * value)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawXLines(canvas)
        drawYLines(canvas)
        drawPlot(canvas)
    }

    private fun drawPlot(canvas: Canvas) {
        val maxX = canvas.width.toFloat()
        val maxY = canvas.height.toFloat()
        val areaX = maxX - 2 * xMargin

        for (line in plotLines) {
            if (line.isEmpty() || !line.visible) continue

            val items = line.getDataPoints()
            if (items.isEmpty()) continue

            val prePlotPoints = ArrayList<PlotPoint>()
            for (index in items.indices) {
                val item = items[index]
                prePlotPoints.add(PlotPoint(
                    if (displayItemCount != null) {
                        PlotLineItem.cord(displayItemCount!! - items.size +  index, 0, displayItemCount!! - 1)
                    } else {
                        line.x(item.Calendar)
                    },
                    line.y(item.Value)!!
                ))
            }

            val postPlotPoints = ArrayList<PlotPoint>()
            val groupCount = areaX / 5
            val groupBy = 5 / areaX

            for (group in prePlotPoints.groupBy { (it.x / groupBy).toInt() }) {
                val min = group.value.minBy { it.x }?.x!!
                val max = group.value.maxBy { it.x }?.x!!
                postPlotPoints.add(PlotPoint(
                    x(min(max(min + (max - min) * (1f / groupCount * group.key), 0f), 1f), 1f, maxX)!!,
                    y(group.value.map { it.y }.average().toFloat(), 1f, maxY)!!
                ))
            }

            val path = Path()
            var prevPoint: PlotPoint? = null

            for (i in postPlotPoints.indices) {
                val point = postPlotPoints[i]

                if (i == 0) {
                    path.moveTo(point.x, point.y)
                    if (postPlotPoints.size == 1) {
                        path.lineTo(point.x, point.y)
                    }
                } else if (prevPoint != null) {
                    val midX = (prevPoint.x + point.x) / 2
                    val midY = (prevPoint.y + point.y) / 2

                    if (i == 1) {
                        path.lineTo(midX, midY)
                    } else {
                        path.quadTo(prevPoint.x, prevPoint.y, midX, midY)
                    }
                }

                prevPoint = point
            }

            if (prevPoint != null) {
                path.lineTo(prevPoint.x, prevPoint.y)
            }

            canvas.drawPath(path, line.plotPaint!!.Plot)
        }
    }

    private fun drawXLines(canvas: Canvas) {
        val maxX = canvas.width.toFloat()
        val maxY = canvas.height.toFloat()

        for (i in 0 until xLineCount) {
            var label: String

            if (displayItemCount != null) {
                label = String.format(
                    "%dkm",
                    abs((displayItemCount!! - 1) / 10 - i * (displayItemCount!! - 1) / (xLineCount - 1) / 10)
                )
            } else {
                var duration = 0L

                for (line in plotLines) {
                    if (line.duration != null && duration < line.duration!!) {
                        duration = line.duration!!
                    }
                }

                val x = duration.toFloat() / (xLineCount - 1) * i
                label = String.format("%02d:%02d", (x / 60).toInt(), (x % 60).toInt())
            }

            val xCord = x(i.toFloat(), xLineCount.toFloat() - 1, maxX)!!
            val yCord = maxY - yMargin

            val bounds = Rect()

            labelPaint.getTextBounds(label, 0, label.length, bounds)

            canvas.drawText(
                label,
                xCord - bounds.width() / 2,
                yCord + yMargin / 2 + bounds.height() / 2,
                labelPaint
            )

            val path = Path()
            path.moveTo(xCord, yMargin.toFloat())
            path.lineTo(xCord, yCord)
            canvas.drawPath(path, labelLinePaint)
        }
    }

    private fun drawYLines(canvas: Canvas) {
        val maxX = canvas.width.toFloat()
        val maxY = canvas.height.toFloat()

        for (i in 0 until yLineCount) {
            val cordY = y(i.toFloat(), yLineCount.toFloat() - 1, maxY)!!

            val path = Path()
            path.moveTo(xMargin.toFloat(), cordY)
            path.lineTo(maxX - xMargin, cordY)
            canvas.drawPath(path, labelLinePaint)
        }

        for (line in plotLines) {
            if (line.isEmpty() || !line.visible) continue

            val bounds = Rect()
            labelPaint.getTextBounds("Dummy", 0, "Dummy".length, bounds)

            val labelShiftY = (bounds.height() / 2).toFloat()
            val valueShiftY = line.range() / (yLineCount - 1)

            var labelCordX: Float? = null

            if (line.LabelPosition === PlotLabelPosition.LEFT) labelCordX = textSize
            if (line.LabelPosition === PlotLabelPosition.RIGHT) labelCordX = maxX - xMargin + textSize

            val highlightCordY = y(line, maxY)

            if (line.LabelPosition !== PlotLabelPosition.NONE && labelCordX != null) {
                if (line.Unit.isNotEmpty()) {
                    canvas.drawText(
                        line.Unit,
                        labelCordX,
                        yMargin - (yMargin / 3f),
                        labelPaint
                    )
                }

                for (i in 0 until yLineCount) {
                    val valueY = line.max() - i * valueShiftY
                    val cordY = y(valueY, line.max(), maxY)!!
                    val label = String.format(line.LabelFormat, valueY / line.Divider)

                    if (highlightCordY == null || abs(cordY - highlightCordY) > textSize) {
                        canvas.drawText(label, labelCordX, cordY + labelShiftY, labelPaint)
                    }
                }
            }

            if (labelCordX != null) {
                canvas.drawText(
                    String.format(
                        line.HighlightFormat,
                        line.highlight()!! / line.Divider
                    ), labelCordX, highlightCordY!! + labelShiftY, line.plotPaint!!.HighlightLabel
                )
            }

            for (baseLineAt in line.baseLineAt) {
                val baseCordY = y(baseLineAt, maxY)
                if (baseCordY != null) {
                    val basePath = Path()
                    basePath.moveTo(xMargin.toFloat(), baseCordY)
                    basePath.lineTo(maxX - xMargin, baseCordY)
                    canvas.drawPath(basePath, baseLinePaint)
                }
            }

            if (highlightCordY != null && line.HighlightMethod === PlotHighlightMethod.AVG) {
                val highlightPath = Path()
                highlightPath.moveTo(xMargin.toFloat(), highlightCordY)
                highlightPath.lineTo(maxX - xMargin, highlightCordY)
                canvas.drawPath(highlightPath, line.plotPaint!!.HighlightLabelLine)
            }
        }
    }
}