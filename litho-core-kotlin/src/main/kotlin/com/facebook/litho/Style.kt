/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.litho

import android.graphics.drawable.Drawable
import android.util.SparseArray
import android.view.ViewOutlineProvider
import androidx.annotation.ColorInt
import com.facebook.litho.drawable.ComparableColorDrawable

/** Enums for [ObjectStyleItem]. */
private enum class ObjectField {
  BACKGROUND,
  FOREGROUND,
  ON_CLICK,
  ON_LONG_CLICK,
  WRAP_IN_VIEW,
  VIEW_TAG,
  VIEW_TAGS,
  OUTLINE_PROVIDER,
}

/** Enums for [FloatStyleItem]. */
private enum class FloatField {
  ALPHA,
  ELEVATION,
}

/**
 * Part of a [Style] that can apply an attribute to an underlying Component, e.g. width or click
 * handling.
 */
interface StyleItem {

  /** Sets this style item value on the given [Component]. */
  fun applyToComponent(resourceResolver: ResourceResolver, component: Component)
}

/** Common style item for all object styles. See note on [DimenField] about this pattern. */
private class ObjectStyleItem(val field: ObjectField, val value: Any?) : StyleItem {
  override fun applyToComponent(resourceResolver: ResourceResolver, component: Component) {
    val commonProps = component.getOrCreateCommonProps()
    when (field) {
      ObjectField.BACKGROUND -> commonProps.background(value as Drawable?)
      ObjectField.FOREGROUND -> commonProps.foreground(value as Drawable?)
      ObjectField.ON_CLICK ->
          commonProps.clickHandler(eventHandler(value as ((ClickEvent) -> Unit)))
      ObjectField.ON_LONG_CLICK ->
          commonProps.longClickHandler(
              eventHandlerWithReturn(value as ((LongClickEvent) -> Boolean)))
      ObjectField.WRAP_IN_VIEW -> commonProps.wrapInView()
      ObjectField.VIEW_TAG -> commonProps.viewTag(value)
      ObjectField.VIEW_TAGS -> commonProps.viewTags(value as SparseArray<Any>)
      ObjectField.OUTLINE_PROVIDER -> commonProps.outlineProvider(value as ViewOutlineProvider?)
    }.exhaustive
  }
}

/** Common style item for all float styles. See note on [FloatField] about this pattern. */
private class FloatStyleItem(val field: FloatField, val value: Float) : StyleItem {
  override fun applyToComponent(resourceResolver: ResourceResolver, component: Component) {
    val commonProps = component.getOrCreateCommonProps()
    when (field) {
      FloatField.ALPHA -> commonProps.alpha(value)
      FloatField.ELEVATION -> commonProps.shadowElevationPx(value)
    }.exhaustive
  }
}

/** exposed to avoid package-private error on [Component] */
internal fun Component.getCommonPropsHolder() = getOrCreateCommonProps()

internal fun Component.getOrCreateCommonDynamicPropsHolder() = getOrCreateCommonDynamicProps()

/**
 * An immutable ordered collection of attributes ( [StyleItem] s) that can be applied to a
 * component, like width or click handling.
 *
 * Adding new attributes to a Style (e.g. by calling `.background`) will return a new Style object.
 * Ordering matters in that the last definition of an attribute 'wins'.
 *
 * Styles can also be added via the `+` operator, with attributes from the right-hand side Style
 * taking precedence if the two define different values for the same attribute, similar to adding
 * maps.
 */
open class Style(
    /**
     * This is the Style we're adding to, e.g. `Style.padding()` when calling `.background()` in
     * `Style.padding().background()`
     */
    private val previousStyle: Style?,

    /**
     * This is the [StyleItem] we're adding, e.g. the background when calling `.background()` in
     * `Style.padding().background()`
     */
    private val item: StyleItem?,
) {

  operator fun plus(other: Style?): Style {
    if (other == null) {
      return this
    }
    return CombinedStyle(if (this == Style) null else this, other)
  }

  inline operator fun plus(nextItem: StyleItem?): Style {
    if (nextItem == null) {
      return this
    }
    return Style(if (this == Style) null else this, nextItem)
  }

  fun background(background: Drawable?) = this + ObjectStyleItem(ObjectField.BACKGROUND, background)

  fun backgroundColor(@ColorInt backgroundColor: Int) =
      this +
          ObjectStyleItem(ObjectField.BACKGROUND, ComparableColorDrawable.create(backgroundColor))

  fun foreground(foreground: Drawable?) = this + ObjectStyleItem(ObjectField.FOREGROUND, foreground)

  fun onClick(onClick: (ClickEvent) -> Unit) = this + ObjectStyleItem(ObjectField.ON_CLICK, onClick)

  fun onLongClick(onLongClick: (LongClickEvent) -> Boolean) =
      this + ObjectStyleItem(ObjectField.ON_LONG_CLICK, onLongClick)

  fun wrapInView() = this + ObjectStyleItem(ObjectField.WRAP_IN_VIEW, null)

  fun viewTag(viewTag: Any) = this + ObjectStyleItem(ObjectField.VIEW_TAG, viewTag)

  fun viewTags(viewTags: SparseArray<out Any>) =
      this + ObjectStyleItem(ObjectField.VIEW_TAGS, viewTags)

  fun alpha(alpha: Float) = this + FloatStyleItem(FloatField.ALPHA, alpha)

  fun elevation(elevation: Float) = this + FloatStyleItem(FloatField.ELEVATION, elevation)

  fun outlineProvider(outlineProvider: ViewOutlineProvider?) =
      this + ObjectStyleItem(ObjectField.OUTLINE_PROVIDER, outlineProvider)

  open fun forEach(lambda: (StyleItem) -> Unit) {
    previousStyle?.forEach(lambda)
    if (item != null) {
      lambda(item)
    }
  }

  internal fun applyToComponent(resourceResolver: ResourceResolver, component: Component) {
    forEach { it.applyToComponent(resourceResolver, component) }
  }

  /**
   * An empty Style singleton that can be used to build a chain of style items.
   *
   * This is a bit of a trick that lets us make `Style.background()` look like a static call, but
   * actually be a member call since `Style` is now a singleton object. Otherwise we'd need to
   * define both a static `Style.background()` and a member function to support
   * `Style.padding(...).background()`.
   */
  companion object : Style(null, null)
}

/**
 * A subclass of [Style] which combines two Styles, as opposed to adding a single [StyleItem] to an
 * existing Style.
 *
 * A CombinedStyle is created by combining two Styles with `+`. Attributes from the right-hand side
 * Style take precedence if the two define different values for the same attribute, similar to
 * adding maps.
 */
private class CombinedStyle(val first: Style?, val second: Style?) : Style(first, null) {

  override fun forEach(lambda: (StyleItem) -> Unit) {
    first?.forEach(lambda)
    second?.forEach(lambda)
  }
}
