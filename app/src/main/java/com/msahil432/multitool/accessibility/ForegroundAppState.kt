package com.msahil432.multitool.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory state holder tracking the currently focused foreground package name.
 */
object ForegroundAppState {
  private val _currentPackage = MutableStateFlow("")

  /** StateFlow emitting the current foreground application package name. */
  val currentPackage: StateFlow<String> = _currentPackage.asStateFlow()

  /** Updates the active foreground package name if it changed. */
  fun update(pkg: String) {
    if (pkg.isNotBlank() && _currentPackage.value != pkg) {
      _currentPackage.value = pkg
    }
  }
}

