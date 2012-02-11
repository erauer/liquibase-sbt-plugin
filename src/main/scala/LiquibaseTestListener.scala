package com.github.sdb.sbt.liquibase

import sbt._

class LiquibaseTestListener(liquibaseConfig :  Set[LiquibaseConfiguration], liquibaseClassPath :  Option[ClassLoader]) extends TestsListener with LiquibaseRunner {
	
    SBTLogger.logger = Some(ConsoleLogger())
    val log : ConsoleLogger = ConsoleLogger()
	
	def doComplete(status: TestResult.Value) = {}
	
	def doInit {
		if (!liquibaseConfig.isEmpty) {
			liquibaseConfig foreach (config => 
			liquibaseUpdate(config,liquibaseClassPath,None))
		}
	}
		
	def startGroup(name: String) = { }

	def testEvent(event: TestEvent) = {}
		
	def endGroup(name: String, result: TestResult.Value) ={}
	
	def endGroup(name: String, t: Throwable) = {}
}