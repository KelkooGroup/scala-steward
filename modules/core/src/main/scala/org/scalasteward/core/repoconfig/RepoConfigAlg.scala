/*
 * Copyright 2018-2019 scala-steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalasteward.core.repoconfig

import cats.data.OptionT
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.circe.config.parser
import org.scalasteward.core.vcs.data.Repo
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.model.Update
import org.scalasteward.core.repoconfig.RepoConfigAlg._
import org.scalasteward.core.util.MonadThrowable

final class RepoConfigAlg[F[_]](
    implicit
    fileAlg: FileAlg[F],
    logger: Logger[F],
    workspaceAlg: WorkspaceAlg[F],
    F: MonadThrowable[F]
) {
  def getRepoConfig(repo: Repo): F[RepoConfig] =
    workspaceAlg.repoDir(repo).flatMap { dir =>
      val configFile = dir / repoConfigBasename
      val maybeRepoConfig = OptionT(fileAlg.readFile(configFile)).flatMapF { content =>
        parseRepoConfig(content) match {
          case Right(config)  => logger.info(s"Parsed $config") >> F.pure(config.some)
          case Left(errorMsg) => logger.info(errorMsg).as(none[RepoConfig])
        }
      }
      maybeRepoConfig.getOrElse(RepoConfig())
    }
}

object RepoConfigAlg {
  val repoConfigBasename: String = ".scala-steward.conf"

  def parseRepoConfig(input: String): Either[String, RepoConfig] =
    parser.decode[RepoConfig](input).leftMap { error =>
      s"Failed to parse $repoConfigBasename: ${error.getMessage}"
    }

  def configToIgnoreFurtherUpdates(update: Update): String =
    update match {
      case s: Update.Single =>
        s"""updates.ignore = [{ groupId = "${s.groupId}", artifactId = "${s.artifactId}" }]"""
      case g: Update.Group =>
        s"""updates.ignore = [{ groupId = "${g.groupId}" }]"""
    }
}
