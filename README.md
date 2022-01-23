# When is my ride?

![Build status](https://github.com/jackrr/when-is-my-ride/actions/workflows/main.yaml/badge.svg?branch=main)

A simple app that enables. This app is free to use and hosted at
https://whenismyride.com.

## Contributions

Contributions are welcome. Feel free to open an issue describing the changes
you'd like to see and we'll go from there.

## Running the app

You'll need a clojure environment and leiningen installed.

### Environment

Acquire a free MTA access token at https://new.mta.info/developers. In
development add a file called `resources/secrets.edn` with the following:

``` clojure
{"MTA_API_KEY" "YOUR_TOKEN_HERE"}
```

In production set an `MTA_API_KEY` env var containing the token.

## Data feeds

At this time, all data sources use a combination of GTFS static and GTFS
realtime.

This application is built with static data in resources/.

NYC Ferry data is fully public and can be found at
https://www.ferry.nyc/developer-tools/. MTA data requires a free access token
and can be found at https://new.mta.info/developers.

## License

Copyright © 2022 Jack Ratner

This program and the accompanying materials are made available under the terms
of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
