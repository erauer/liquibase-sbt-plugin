package com.github.sdb.sbt.liquibase

import liquibase.logging.core.AbstractLogger
import liquibase.servicelocator.ServiceLocator

import sbt._

object SBTLogger {

  // make sure that our custom Liquibase logger can be discovered by the
  // service locator
  ServiceLocator.getInstance.addPackageToScan("com.github.sdb.sbt.liquibase")

  var logger: Option[Logger] = None

  def log(l: String, m: String, e: Throwable) {
    logger match {
      case Some(log) => {
        l match {
          case "debug" => log.debug(m)
          case "info"  => log.info(m)
          case "warn"  => log.warn(m)
          case "error" => log.error(m)
        }
        if (e != null) log.trace(e)
      }
      case None => {}
    }
  }
}

/**
 * Custom Liquibase logger to redirect logging to the project logger.
 */
class SBTLogger extends AbstractLogger {
  import SBTLogger._
  def getPriority = 10
  def setLogLevel(l: String, f: String) {} // ignored 
  def setName(n: String) {} // ignored 

  def debug(m: String, e: Throwable) { log("debug", m, e) }
  def debug(m: String) { log("debug", m, null) }
  def info(m: String, e: Throwable) { log("info", m, e) }
  def info(m: String) { log("info", m, null) }
  def warning(m: String, e: Throwable) { log("warn", m, e) }
  def warning(m: String) { log("warn", m, null) }
  def severe(m: String, e: Throwable) { log("error", m, e) }
  def severe(m: String) { log("error", m, null) }
}