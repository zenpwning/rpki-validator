/**
 * The BSD License
 *
 * Copyright (c) 2010-2012 RIPE NCC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the RIPE NCC nor the names of its contributors may be
 *     used to endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.ripe.rpki.validator.store

import java.sql.ResultSet
import javax.sql.DataSource
import java.net.URI
import java.io.File
import scala.collection.JavaConversions._

import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

import scala.util.Try

class HttpFetcherStore(dataSource: DataSource) {

  val template: NamedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource)

  def clear() = {
    for (t <- Seq("latest_http_snapshot"))
      template.update(s"TRUNCATE TABLE $t", Map[String, Object]())
  }

  def storeSerial(url: URI, sessionId: String, serial: BigInt) = {
    val count = template.update(
      """UPDATE latest_http_snapshot SET serial_number = :serial_number
         WHERE url = :url
         AND session_id = :session_id
      """,
      Map("url" -> url.toString,
        "session_id" -> sessionId,
        "serial_number" -> serial.toString))

    if (count == 0) {
      template.update(
        """INSERT INTO latest_http_snapshot(url, session_id, serial_number)
           SELECT :url, :session_id, :serial_number
           WHERE NOT EXISTS (
             SELECT * FROM latest_http_snapshot s
             WHERE s.url = :url
           )
      """,
        Map("url" -> url.toString,
          "session_id" -> sessionId,
          "serial_number" -> serial.toString))
      }
  }

  def getSerial(url: URI, sessionId: String): Option[BigInt] = {
    Try {
      template.queryForObject(
        "SELECT serial_number FROM latest_http_snapshot WHERE url = :url AND session_id = :session_id",
        Map("url" -> url.toString, "session_id" -> sessionId),
        new RowMapper[BigInt]() {
          override def mapRow(rs: ResultSet, i: Int) = new BigInt(rs.getBigDecimal(1).toBigInteger)
        }
      )
    }.toOption
  }
}

object HttpFetcherStore extends SimpleSingletons[String, HttpFetcherStore]({
  path =>
    new HttpFetcherStore(DataSources.DurableDataSource(new File(path)))
}) {
  def inMemory(taName: String): HttpFetcherStore = new HttpFetcherStore(DataSources.InMemoryDataSource)
}
