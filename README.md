## Sender app


The Sender is a simple application that is able to transfer JSON messages from a source into one or several destinations according to specified rules. 

It supports two sources at the moment: HTTP request and Kafka topic and multiple different destinations: Mandrill, Mailgun, HTTP request, Kafka or SMS gateway. 

To configure any of receivers one should use following config template: 

```conf
kafka-receiver {
  brokerList = "kafka:9092"
  zooKeeperHost = "zookeper:2181"
  topicName = "sample-topic"
  groupId = "sender"
}

http-receiver {
  host = localhost
  port = 6080
}
```

We define one Kafka topic reader here and the HTTP receiver which will start the HTTP server on the 6080 port. Those receivers names are pre-defined by default in the reference config but a user can create as many HTTP/Kafka readers as it wants to. One should check out `reference.conf` for example.

Defining output destinations is similar to input:

```
mandrill {
  key = "secret-mandrill-key"
  fromName = "Admin"
  fromEmail = "noreply@domain"
}

mailgun {
  domain = "domain-name"
  key = "secret-mailgun-key"
  fromEmail = "noreply@domain"
}

```

Again, a user can define as many destinations of the same type as it want's to, but those are pre-defined in the reference config.

Rules define a filter for incoming messages and how they should be transformed and their destination.
Sender allows, for example, to get all messages from some Kafka topic, filter email confirmation events from all events stream (by some field value) and send it to email or SMS. Or it can transform all messages from one Kafka topic and put them into another, or even just accept HTTP messages and route them into Kafka.

Rules are defined in the same config as array of `if`-`do` blocks:
 
```
rules = [
  {
    if {
      meta.source = kafka-receiver
      meta.topic = sample-topic
      body.operation = email-confirmation
    }
    do {
      send-by=mailgun
      template="/conf/templates/email-confirm.mustache"
      destination="{{email}}"
      subject="Confirm Your Registration"
      important=true
      continue=false
    }
  },{
    if {
      meta.source = kafka-receiver
      meta.topic = sample-topic
      body.operation = password-recovery
    }
    do {
      send-by=mailgun
      template="/conf/templates/recovery.mustache"
      destination="{{email}}"
      subject="Password recovery"
      important=true
      continue=false
    }
  }
]

```

Here we define two filters, both filter messages source to `kafka-receiver` and `sample-topic`. Each receiver creates metadata Map in message specifying receiver parameters. Additionally, messages are filtered by a field in the message body (in JSON format) which should equal to specified value. Block `do` defines destination of the message and output message parameters. If the example message is routed to the Mailgun sender, the message body is replaced by Mustache template defined in a file (also any URL or inline Mustache format is acceptable). Template can use any fields of the message body or metadata. Email destination in both cases obtained from JSON body as field `email`. Parameter `important=true` is specific to Mailgun and increases message priority.
Parameter `continue=false` is common and says that this is termination rule for this message, otherwise, the message will be sent to following rules.
