# The build environment: JDK 21 and Maven, both from the same pinned nixpkgs revision so that a
# local build and a CI build see the same toolchain. The revision is the one this project was set up
# with; bumping it is a deliberate change, not something a channel does behind our back.

let
  nixpkgs = fetchTarball {
    url = "https://github.com/NixOS/nixpkgs/archive/e80236013dc8.tar.gz";
    sha256 = "0g9l9iy22mb3sjhh2rs2f5gh0iv6i280hhpvqj11jwrbg1akkia3";
  };
  pkgs = import nixpkgs { };
in
pkgs.mkShell {
  packages = [
    pkgs.jdk21
    pkgs.maven
  ];

  LC_ALL = "C.UTF-8";
  LANG = "C.UTF-8";

  # JAVA_HOME points at the JDK of this shell, not at the one the machine happens to have
  shellHook = ''
    JAVA_HOME="$(cd "$(dirname "$(readlink -f "$(command -v javac)")")/.." && pwd)"
    export JAVA_HOME
    export PATH="$JAVA_HOME/bin:$PATH"
  '';
}
