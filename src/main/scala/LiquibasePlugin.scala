package com.github.sdb.sbt.liquibase

import sbt._
import sbt.Project.Initialize
import sbt.complete._
import sbt.Keys._
import sbt.EvaluateTask._
import sbt.Load.BuildStructure
import sbt.CommandSupport._
import complete.DefaultParsers._

import sbt.classpath.ClasspathUtilities


import liquibase.integration.commandline.CommandLineUtils
import liquibase.resource.FileSystemResourceAccessor
import liquibase.Liquibase
import liquibase.exception._



import java.text.DateFormat
							
								
object LiquibasePlugin extends Plugin with LiquibaseRunner {

  private val CLEAR_CHECKSUMS = "clear-checksums"
  private val DROP = "drop"
  private val ROLLBACK = "rollback"
  private val ROLLBACK_COUNT = "rollback-count"
  private val ROLLBACK_DATE = "rollback-date"
  private val TAG = "tag"
  private val UPDATE = "update"
  private val UPDATE_COUNT = "update-count"
  private val VALIDATE = "validate"

  import sbt.complete.DefaultParsers.{token,Space,NotSpace}
  import sbt.complete.Parser._

  val liquibaseOptions = SettingKey[Map[String,Seq[LiquibaseConfiguration]]]("liquibase-options")  
  val liquibaseTestConfig = SettingKey[Option[String]]("liquibase-test-config")
  

  val liquibase = InputKey[Unit]("liquibase")

  


  SBTLogger.logger = Some(ConsoleLogger())
  val log : ConsoleLogger = ConsoleLogger()

  private def dbParser(configs : Map[String,Seq[LiquibaseConfiguration]]) = {
	if (!configs.isEmpty) {
	 val literals = configs.keySet.map(literal(_)).toSeq
	 oneOf[String](literals)
	} else 
	 literal("default")
  }  

  val parser : Initialize[State => Parser[((String,Option[String]),Option[Seq[String]])]] =  
			(liquibaseOptions) { (liquibaseOptions: Map[String,Seq[LiquibaseConfiguration]]) =>
				(state: State) =>
				val dbTokens = dbParser(liquibaseOptions)
	            val tokens = (token(Space) ~>
	 			  (token(Space ~> CLEAR_CHECKSUMS)  ~ token(Space ~> dbTokens).? ~ (token(literal(" ").*)).?)   |
				  (token(Space ~> DROP)  ~ token(Space ~> dbTokens).? ~ ((token(Space ~> NotSpace)).*).? ) |
				  (token(Space ~> ROLLBACK)  ~ token(Space ~> dbTokens).? ~ ((token(Space ~> NotSpace)).*).? ) |
				  (token(Space ~> ROLLBACK_COUNT)  ~ token(Space ~> dbTokens).? ~ ((token(Space ~> NotSpace)).*).? ) |	
				  (token(Space ~> ROLLBACK_DATE)  ~ token(Space ~> dbTokens).? ~ ((token(Space ~> NotSpace)).*).? ) |
				  (token(Space ~> TAG)  ~ token(Space ~> dbTokens).? ~ ((token(Space ~> NotSpace)).*).? ) |	
				  (token(Space ~> UPDATE)  ~ token(Space ~> dbTokens).? ~ ((token(Space ~> NotSpace)).*).? ) |
				  (token(Space ~> UPDATE_COUNT)  ~ token(Space ~> dbTokens).? ~ ((token(Space ~> NotSpace)).*).? ) |	
				  (token(Space ~> VALIDATE)  ~ token(Space ~> dbTokens).? ~ ((token(Space ~> NotSpace)).*).? )
				)
				tokens	
   }


   private def getLiquibaseConfig(key : Option[String], configs :Map[String,Seq[LiquibaseConfiguration]]) : Option[Seq[LiquibaseConfiguration]] = {
		configs.get(key.getOrElse("default")).orElse(None)
   }

   val taskDef  = (parsedTask: TaskKey[((String,Option[String]),Option[Seq[String]])])  => {
      // we are making a task, so use 'map'
	  (fullClasspath in Runtime,liquibaseOptions in Runtime,parsedTask) map {
          case (runtimeCP : Seq[Attributed[File]],liquibaseOptions: Map[String,Seq[LiquibaseConfiguration]],
			((cmd: String, db: Option[String]),args : Option[Seq[String]])) => {
  			val liquibaseConfiguration = getLiquibaseConfig(db,liquibaseOptions)
			val cp = ClasspathUtilities.toLoader(Build.data(runtimeCP))
			val liquibaseClasspath = if (cp != null) Some(cp) else None

	   		liquibaseConfiguration foreach(
	   			configs => configs.foreach (config => {
				  cmd match {
					case CLEAR_CHECKSUMS => liquibaseClearChecksums(config,liquibaseClasspath)
					case DROP => liquibaseDropAll(config,liquibaseClasspath,args)
					case ROLLBACK => liquibaseRollback(config,liquibaseClasspath,args)
					case ROLLBACK_COUNT => liquibaseRollbackCount(config,liquibaseClasspath,args)
					case ROLLBACK_DATE => liquibaseRollbackDate(config,liquibaseClasspath,args)
					case TAG => liquibaseTag(config,liquibaseClasspath,args)
					case UPDATE => liquibaseUpdate(config,liquibaseClasspath,None)
					case UPDATE_COUNT => liquibaseUpdateCount(config,liquibaseClasspath,args)
					case VALIDATE => liquibaseValidate(config,liquibaseClasspath)
					case _ => log.error("Found an unknow command of " + cmd); None	
				  }
	   			})
	   		)		
  	        }
	}
   }

   private def liquibaseTestListenerTask: Initialize[Task[TestReportListener]] =
    (fullClasspath in Runtime,liquibaseOptions in Runtime, liquibaseTestConfig in Runtime) map {
      (fullClasspath,liquibaseOptions, liquibaseTestConfig) => {
			val cp = ClasspathUtilities.toLoader(Build.data(fullClasspath))
			val liquibaseClasspath = if (cp != null) Some(cp) else None
			var liquibaseConfigs : Option[Seq[LiquibaseConfiguration]] = None
			if (liquibaseTestConfig.isDefined) {
				liquibaseConfigs = getLiquibaseConfig(liquibaseTestConfig,liquibaseOptions)
			}
			new LiquibaseTestListener(liquibaseConfigs,liquibaseClasspath)
	   }
    }

    override def settings = Seq(
      liquibase  <<= InputTask(parser)(taskDef),
	  liquibaseTestConfig := None,
      testListeners <+= liquibaseTestListenerTask
    ) 
	
}




