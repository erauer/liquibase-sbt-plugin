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

  val liquibaseOptions = SettingKey[Map[String,Seq[LiquibaseConfiguration]]]("liquibase-options")  

  val liquibase = InputKey[Unit]("liquibase")


  SBTLogger.logger = Some(ConsoleLogger())
  private val log = ConsoleLogger()

  private def dbParser(configs : Map[String,Seq[LiquibaseConfiguration]]) = {
	if (!configs.isEmpty) {
	 val literals = configs.keySet.map(literal(_)).toSeq
	 oneOf[String](literals)
	} else 
	 literal("default")
  }  

  def liquibaseDateFormat: DateFormat = DateFormat.getDateInstance()

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



    override def settings = Seq(
      liquibase  <<= InputTask(parser)(taskDef)
    ) 

	
  private def liquibaseUpdate(liquibaseConfiguration : LiquibaseConfiguration,cp : Option[ClassLoader], context : Option[String]) {
    (new LiquibaseAction(liquibaseConfiguration,cp,{lb => lb update
	 	liquibaseConfiguration.contexts.getOrElse(null); None }) with Cleanup
	).run
  }

  private def liquibaseDropAll(liquibaseConfiguration : LiquibaseConfiguration,cp : Option[ClassLoader], args : Option[Seq[java.lang.String]]) = {
    (new LiquibaseAction(liquibaseConfiguration,cp,{ lb =>
      args match {
        case None => {
				System.out.println("drop all")
				 lb dropAll
		}
        case _ => lb dropAll (args.get:_*)
      }; None}) with Cleanup).run 
  }

  private def liquibaseClearChecksums(liquibaseConfiguration : LiquibaseConfiguration,cp : Option[ClassLoader]) {
	(new LiquibaseAction(liquibaseConfiguration,cp,{ lb => lb clearCheckSums; None }) with Cleanup).run
  }

  private def liquibaseRollback(liquibaseConfiguration : LiquibaseConfiguration,cp : Option[ClassLoader], args : Option[Seq[java.lang.String]]) {
    (new LiquibaseAction(liquibaseConfiguration,cp,{ lb =>
      args match {
	    case None => Some("The tag must be specified.")
        case _ => lb rollback(args.get.head, liquibaseConfiguration.contexts.getOrElse(null)); None
      }
    }) with Cleanup).run 	
  }

  private def liquibaseRollbackCount(liquibaseConfiguration : LiquibaseConfiguration,cp : Option[ClassLoader], args : Option[Seq[java.lang.String]]) {
    (new LiquibaseAction(liquibaseConfiguration,cp,{ lb =>
      args match {
	    case None => Some("Number of change sets must be specified.")
        case _ => args.get.head match {
          case Int(x) => lb rollback(x, liquibaseConfiguration.contexts.getOrElse(null)); None
          case _ => Some("Number of change sets must be an integer value.")
        }
      }
    }) with Cleanup).run 	
  }

  private def liquibaseRollbackDate(liquibaseConfiguration : LiquibaseConfiguration, cp : Option[ClassLoader], args : Option[Seq[java.lang.String]]) {
    (new LiquibaseAction(liquibaseConfiguration,cp,{ lb =>
      args match {
	    case None => Some("Date must be specified.")
        case _ => args.get.head match {
          case Date(x) => lb rollback(x,  liquibaseConfiguration.contexts.getOrElse(null)); None
          case _ => Some("The format of the date must match that of 'liquibaseDateFormat'.")
        }
      }
    }) with Cleanup).run	
  }

  private def liquibaseTag(liquibaseConfiguration : LiquibaseConfiguration, cp : Option[ClassLoader], args : Option[Seq[java.lang.String]]) {
	(new LiquibaseAction(liquibaseConfiguration,cp, { lb =>
      args match {
	    case None => Some("The tag must be specified.")
        case _ => lb tag args.get.head; None
      }
    }) with Cleanup).run
  }

  private def liquibaseUpdateCount(liquibaseConfiguration : LiquibaseConfiguration, cp : Option[ClassLoader], args : Option[Seq[java.lang.String]]) {
    (new LiquibaseAction(liquibaseConfiguration,cp, { lb =>
      args match {
	    case None => Some("Number of change sets must be specified.")
        case _ => args.get.head match {
          case Int(x) => lb update(x, liquibaseConfiguration.contexts.getOrElse(null)); None
          case _ => Some("Number of change sets must be an integer value.")
        }
      }
    }) with Cleanup).run
  }	

  private def liquibaseValidate(liquibaseConfiguration : LiquibaseConfiguration, cp : Option[ClassLoader]) {
    (new LiquibaseAction(liquibaseConfiguration,cp, { lb => lb validate; None }) with Cleanup).run	
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

  private implicit def action2Result(a: LiquibaseAction) : Unit = a.run

  abstract class LiquibaseAction(liquibaseConfiguration : LiquibaseConfiguration, liquibaseClassPath :  Option[ClassLoader], action: Liquibase => Option[String]) {
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


