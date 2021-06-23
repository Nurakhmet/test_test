public class Student{
	private long id;
	private String name;
	private String surname;
	private int age;

	public Student(){

	}

	public Student(long id, String name, String surname, int age){
		this.id = id;
		this.name = name;
		this.surname = surname;
		this.age = age;
	}

	public void setId(Long id){
		this.id = id;
	}

		public void setName(String name){
		this.name = name;
	}

		public void setSurname(String surname){
		this.surname = surname;
	}

		public void setAge(int age){
		this.age = age;
	}

	public Long getId(){
		return this.id;
	}

	public String getName(){
		return this.name;
	}

		public String getSurname(){
		return this.surname;
	}

		public int getAge(){
		return this.age;
	}

	public String toString(){
		return "Student{id=" + this.id + ", name=" + this.name + ", surname=" + this.surname + ", age=" + this.age + "}";
	}
}