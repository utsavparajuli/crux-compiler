package crux;

final class Authors {
  // TODO: Add author information.
  static final Author[] all = {new Author("Rohan Jayaram", "21656587", "rjayara1"),
				new Author("Utsav Parajuli", "49849418", "uparajul")};
}


final class Author {
  final String name;
  final String studentId;
  final String uciNetId;

  Author(String name, String studentId, String uciNetId) {
    this.name = name;
    this.studentId = studentId;
    this.uciNetId = uciNetId;
  }
}
