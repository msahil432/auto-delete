package com.msahil432.multitool.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ForegroundAppState {
  private val _currentPackage = MutableStateFlow("")
  val currentPackage: StateFlow<String> = _currentPackage.asStateFlow()

  fun update(pkg: String) {
    if (pkg.isNotBlank() && _currentPackage.value != pkg) {
      _currentPackage.value = pkg
    }
  }
}
