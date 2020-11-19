package utils

import java.io.{PrintWriter, StringWriter}

package object log {
  val stringWriter = new StringWriter
  val printWrite = new PrintWriter(stringWriter)
}
