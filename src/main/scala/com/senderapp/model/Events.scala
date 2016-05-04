package com.senderapp.model

import com.typesafe.config.Config

object Events {
  case class Configure(name: String, config: Config)
}
