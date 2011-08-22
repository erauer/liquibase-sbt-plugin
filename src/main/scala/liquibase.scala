package com.github.sdb.sbt.liquibase

import sbt._
import sbt.complete._
import sbt.Keys._
import sbt.EvaluateTask._
import sbt.Load.BuildStructure
import sbt.CommandSupport._
import sbt.complete.Parsers._

import sbt.classpath.ClasspathUtilities


import liquibase.integration.commandline.CommandLineUtils
import liquibase.resource.FileSystemResourceAccessor
import liquibase.Liquibase
import liquibase.exception._
import liquibase.servicelocator.ServiceLocator


import java.text.DateFormat

case class LiquibaseConfiguration(val changeLogFile : File,
	 							 val url : String,
	 							 val driver : String,
								 val username : Option[String],
								 val password : Option[String],
								 val contexts : Option[String],
								 val defaultSchemaName : Option[String]	)  
							
								
object LiquibasePlugin extends Plugin {

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
  override def settings = Seq(Keys.commands += liquibaseCommand)


  val liquibaseDevOptions = SettingKey[Seq[LiquibaseConfiguration]]("liquibase-dev-options") 
  val liquibaseTestOptions = SettingKey[Seq[LiquibaseConfiguration]]("liquibase-test-options")   


  def liquibaseDateFormat: DateFormat = DateFormat.getDateInstance()


  SBTLogger.logger = Some(ConsoleLogger())
  private val log = ConsoleLogger()

  //TODO: read db config from map
  private def dbParser = {
	if (true)
	 (literal("dev") | literal("test"))
	else 
	 literal("default")
  }  

   private val args = {
	            token(Space) ~>
	 			(token(Space ~> CLEAR_CHECKSUMS)  ~ token(Space ~> dbParser).? ~ (token(literal(" ").*)).?)   |
				(token(Space ~> DROP)  ~ token(Space ~> dbParser).? ~ ((token(Space ~> NotSpace)).*).? ) |
				(token(Space ~> ROLLBACK)  ~ token(Space ~> dbParser).? ~ ((token(Space ~> NotSpace)).*).? ) |
				(token(Space ~> ROLLBACK_COUNT)  ~ token(Space ~> dbParser).? ~ ((token(Space ~> NotSpace)).*).? ) |	
				(token(Space ~> ROLLBACK_DATE)  ~ token(Space ~> dbParser).? ~ ((token(Space ~> NotSpace)).*).? ) |
				(token(Space ~> TAG)  ~ token(Space ~> dbParser).? ~ ((token(Space ~> NotSpace)).*).? ) |	
				(token(Space ~> UPDATE)  ~ token(Space ~> dbParser).? ~ ((token(Space ~> NotSpace)).*).? ) |
				(token(Space ~> UPDATE_COUNT)  ~ token(Space ~> dbParser).? ~ ((token(Space ~> NotSpace)).*).? ) |	
				(token(Space ~> VALIDATE)  ~ token(Space ~> dbParser).? ~ ((token(Space ~> NotSpace)).*).? ) 	
  }

				
   private lazy val liquibaseCommand = Command("liquibase")(_ => args)(doCommand)

   def doCommand(state: State, params: ((String, Option[String]), Option[Seq[String]])) : State = {
		val cmd = params._1._1
		val db = params._1._2 
		val args = params._2

        val extracted = Project.extract(state)
		val buildStruct = extracted.structure

  		val liquibaseConfigurations  : Option[Seq[LiquibaseConfiguration]] = {
			db match {
				case Some("dev") =>  liquibaseDevOptions in extracted.currentRef get buildStruct.data
				case Some("test") => liquibaseTestOptions in extracted.currentRef get buildStruct.data
				case _ => log.error("No environment specified"); None
			}
	
		}

	   System.out.println(liquibaseConfigurations)
	   liquibaseConfigurations match {
			case Some(configs) =>
			configs foreach { config =>
			  cmd match {
				case CLEAR_CHECKSUMS => liquibaseClearChecksums(config,state)
				case DROP => liquibaseDropAll(config,state,args)
				case ROLLBACK => liquibaseRollback(config,state,args)
				case ROLLBACK_COUNT => liquibaseRollbackCount(config,state,args)
				case ROLLBACK_DATE => liquibaseRollbackDate(config,state,args)
				case TAG => liquibaseTag(config,state,args)
				case UPDATE => liquibaseUpdate(config,state,None)
				case UPDATE_COUNT => liquibaseUpdateCount(config,state,args)
				case VALIDATE => liquibaseValidate(config,state)
				case _ => log.error("Found an unknow command of " + cmd); None	
			  }
			}
			case _ => log.error("No liquibase configuration found for " + db); None 
		}
		
		state
	}
	
	
  private def liquibaseUpdate(liquibaseConfiguration : LiquibaseConfiguration,state : State, context : Option[String]) {
    (new LiquibaseAction(liquibaseConfiguration,state,{lb => lb update
	 	liquibaseConfiguration.contexts.getOrElse(null); None }) with Cleanup
	).run
  }

  private def liquibaseDropAll(liquibaseConfiguration : LiquibaseConfiguration,state : State, args : Option[Seq[java.lang.String]]) = {
    (new LiquibaseAction(liquibaseConfiguration,state,{ lb =>
      args match {
        case None => {
				System.out.println("drop all")
				 lb dropAll
		}
        case _ => lb dropAll (args.get:_*)
      }; None}) with Cleanup).run 
  }

  private def liquibaseClearChecksums(liquibaseConfiguration : LiquibaseConfiguration,state : State) {
	(new LiquibaseAction(liquibaseConfiguration,state,{ lb => lb clearCheckSums; None }) with Cleanup).run
  }

  private def liquibaseRollback(liquibaseConfiguration : LiquibaseConfiguration,state : State, args : Option[Seq[java.lang.String]]) {
    (new LiquibaseAction(liquibaseConfiguration,state,{ lb =>
      args match {
	    case None => Some("The tag must be specified.")
        case _ => lb rollback(args.get.head, liquibaseConfiguration.contexts.getOrElse(null)); None
      }
    }) with Cleanup).run 	
  }

  private def liquibaseRollbackCount(liquibaseConfiguration : LiquibaseConfiguration,state : State, args : Option[Seq[java.lang.String]]) {
    (new LiquibaseAction(liquibaseConfiguration,state,{ lb =>
      args match {
	    case None => Some("Number of change sets must be specified.")
        case _ => args.get.head match {
          case Int(x) => lb rollback(x, liquibaseConfiguration.contexts.getOrElse(null)); None
          case _ => Some("Number of change sets must be an integer value.")
        }
      }
    }) with Cleanup).run 	
  }

  private def liquibaseRollbackDate(liquibaseConfiguration : LiquibaseConfiguration, state : State, args : Option[Seq[java.lang.String]]) {
    (new LiquibaseAction(liquibaseConfiguration,state,{ lb =>
      args match {
	    case None => Some("Date must be specified.")
        case _ => args.get.head match {
          case Date(x) => lb rollback(x,  liquibaseConfiguration.contexts.getOrElse(null)); None
          case _ => Some("The format of the date must match that of 'liquibaseDateFormat'.")
        }
      }
    }) with Cleanup).run	
  }

  private def liquibaseTag(liquibaseConfiguration : LiquibaseConfiguration, state : State, args : Option[Seq[java.lang.String]]) {
	(new LiquibaseAction(liquibaseConfiguration,state, { lb =>
      args match {
	    case None => Some("The tag must be specified.")
        case _ => lb tag args.get.head; None
      }
    }) with Cleanup).run
  }

  private def liquibaseUpdateCount(liquibaseConfiguration : LiquibaseConfiguration, state : State, args : Option[Seq[java.lang.String]]) {
    (new LiquibaseAction(liquibaseConfiguration,state, { lb =>
      args match {
	    case None => Some("Number of change sets must be specified.")
        case _ => args.get.head match {
          case Int(x) => lb update(x, liquibaseConfiguration.contexts.getOrElse(null)); None
          case _ => Some("Number of change sets must be an integer value.")
        }
      }
    }) with Cleanup).run
  }	

  private def liquibaseValidate(liquibaseConfiguration : LiquibaseConfiguration, state : State) {
    (new LiquibaseAction(liquibaseConfiguration,state, { lb => lb validate; None }) with Cleanup).run	
  }
	
  object Int {
    def unapply(s: String): Option[Int] = try {
      Some(s.toInt)
    } catch {
      case _ : java.lang.NumberFormatException => None
    }
  }

  object Date {
    import java.text.{DateFormat, ParseException}
    import java.util.Date
    def unapply(s: String): Option[Date] = try {
      Some(liquibaseDateFormat.parse(s))
    } catch {
      case _ : ParseException => None
    }
  }

  private implicit def action2Result(a: LiquibaseAction) = a.run

  abstract class LiquibaseAction(liquibaseConfiguration : LiquibaseConfiguration, state : State, action: Liquibase => Option[String]) {
	 val runtimeCP = Project.evaluateTask(fullClasspath in Runtime,state)
	
	 val liquibaseClassPath : Option[ClassLoader] = {
	   runtimeCP match {
		case  Some(sbt.Value(classpath)) => Some(ClasspathUtilities.toLoader(classpath.files))
		case _ => None
	   } 
	 } 
	
	 val liquibaseDatabase = CommandLineUtils.createDatabaseObject(
			liquibaseClassPath.getOrElse(ClasspathUtilities.rootLoader),
			liquibaseConfiguration.url,
			liquibaseConfiguration.username.getOrElse(null),
			liquibaseConfiguration.password.getOrElse(null),
			liquibaseConfiguration.driver,
			liquibaseConfiguration.defaultSchemaName.getOrElse(null),
			null)

			
	val liquibase = new Liquibase(
			liquibaseConfiguration.changeLogFile.absolutePath,
			new FileSystemResourceAccessor,
			liquibaseDatabase)

			
    def run: Option[String] = {
		System.out.println(action)
		exec({ action(liquibase) })
	}
	
    def exec(f: => Option[String]) = f

  }

  trait Cleanup extends LiquibaseAction {

    override def exec(f: => Option[String]): Option[String] = {
      try { return f }
 	  finally { cleanup }
    }

    def cleanup {
      if (liquibase != null)
        try { liquibase.forceReleaseLocks } catch {
          case e: LiquibaseException => log trace e
        }
      val db = liquibase.getDatabase
      if (db != null)
        try { db.rollback; db.close } catch {
          case e: DatabaseException => log trace e
        }
    }
  }	
	
}


import liquibase.logging.core.AbstractLogger


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


