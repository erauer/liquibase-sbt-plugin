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
								
								
trait LiquibaseRunner {

  val log : ConsoleLogger

  def liquibaseDateFormat: DateFormat = DateFormat.getDateInstance()
		
  def liquibaseUpdate(liquibaseConfiguration : LiquibaseConfiguration,cp : Option[ClassLoader], context : Option[String]) {
    (new LiquibaseAction(liquibaseConfiguration,cp,{lb => lb update
	 	liquibaseConfiguration.contexts.getOrElse(null); None }) with Cleanup
	).run
  }

  def liquibaseDropAll(liquibaseConfiguration : LiquibaseConfiguration,cp : Option[ClassLoader], args : Option[Seq[java.lang.String]]) = {
    (new LiquibaseAction(liquibaseConfiguration,cp,{ lb =>
      args match {
        case None => {
				System.out.println("drop all")
				 lb dropAll
		}
        case _ => lb dropAll (args.get:_*)
      }; None}) with Cleanup).run 
  }

  def liquibaseClearChecksums(liquibaseConfiguration : LiquibaseConfiguration,cp : Option[ClassLoader]) {
	(new LiquibaseAction(liquibaseConfiguration,cp,{ lb => lb clearCheckSums; None }) with Cleanup).run
  }

  def liquibaseRollback(liquibaseConfiguration : LiquibaseConfiguration,cp : Option[ClassLoader], args : Option[Seq[java.lang.String]]) {
    (new LiquibaseAction(liquibaseConfiguration,cp,{ lb =>
      args match {
	    case None => Some("The tag must be specified.")
        case _ => lb rollback(args.get.head, liquibaseConfiguration.contexts.getOrElse(null)); None
      }
    }) with Cleanup).run 	
  }

  def liquibaseRollbackCount(liquibaseConfiguration : LiquibaseConfiguration,cp : Option[ClassLoader], args : Option[Seq[java.lang.String]]) {
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

  def liquibaseRollbackDate(liquibaseConfiguration : LiquibaseConfiguration, cp : Option[ClassLoader], args : Option[Seq[java.lang.String]]) {
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

  def liquibaseTag(liquibaseConfiguration : LiquibaseConfiguration, cp : Option[ClassLoader], args : Option[Seq[java.lang.String]]) {
	(new LiquibaseAction(liquibaseConfiguration,cp, { lb =>
      args match {
	    case None => Some("The tag must be specified.")
        case _ => lb tag args.get.head; None
      }
    }) with Cleanup).run
  }

  def liquibaseUpdateCount(liquibaseConfiguration : LiquibaseConfiguration, cp : Option[ClassLoader], args : Option[Seq[java.lang.String]]) {
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

  def liquibaseValidate(liquibaseConfiguration : LiquibaseConfiguration, cp : Option[ClassLoader]) {
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


abstract class LiquibaseAction(liquibaseConfiguration : LiquibaseConfiguration, liquibaseClassPath :  Option[ClassLoader], action: Liquibase => Option[String]) {

	
	 val liquibaseDatabase = CommandLineUtils.createDatabaseObject(
			liquibaseClassPath.getOrElse(ClasspathUtilities.rootLoader),
			liquibaseConfiguration.url,
			liquibaseConfiguration.username.getOrElse(null),
			liquibaseConfiguration.password.getOrElse(null),
			liquibaseConfiguration.driver,
			liquibaseConfiguration.defaultSchemaName.getOrElse(null),
			null,null)


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


								