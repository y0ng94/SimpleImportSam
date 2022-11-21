package com.inzent.module;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import yn.util.CommonUtil;
import yn.util.Config;
import yn.util.LogUtil;

public class SimpleImportSam {
	private static Logger logger = LoggerFactory.getLogger(SimpleImportSam.class);

	public void run() {
		// SAM 파일 경로 찾기
		long time = System.currentTimeMillis();
		LogUtil.info(logger, "Find directory where sam files are located");

		Path samFilePath = Paths.get(Config.getConfig("INPUT.PATH"));
		if (Files.notExists(samFilePath)) { samFilePath = Paths.get(Config.getConfig("BASEDIR"), Config.getConfig("INPUT.PATH")); }
		if (Files.notExists(samFilePath)) {
			LogUtil.error(logger, "Directory where sam files are located does not exist...");
			return;
		}

		LogUtil.info(logger, "Completed finding directory where sam files are located ( {0}s )", CommonUtil.getTimeElapsed(time));

		// SAM 파일 리스트 만들기
		time = System.currentTimeMillis();
		LogUtil.info(logger, "Reading directory where sam files are located");

		List<Path> samList = new ArrayList<>();
        AtomicInteger index = new AtomicInteger();  
		try {
			samList = Files.list(samFilePath).sorted().collect(Collectors.toList());
			LogUtil.info(logger, "Reading sam file list {0}", samList.stream().map(t -> "\n" + (index.getAndIncrement()+1) + " : " + t).collect(Collectors.joining()));
		} catch (IOException e) {
			e.printStackTrace();
			LogUtil.error(logger,"Error occurred during reading directory where sam files are located ({0})", e.getMessage());
			return;
		}

		LogUtil.info(logger, "Completed reading directory where sam files are located ( {0}s )", CommonUtil.getTimeElapsed(time));

		// 메모리에 클래스 올리기
		time = System.currentTimeMillis();
		try {
			Class.forName(Config.getConfig("DB.CLASS"));
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			LogUtil.error(logger, "Error occurred during load database driver ({0})", e.getMessage());
			return;
		}

		LogUtil.info(logger, "Completed loading database driver ( {0}s )", CommonUtil.getTimeElapsed(time));

		// 파일의 갯수만큼 반복
		int totalCount = 0;
		for (Path samFile : samList) {
			LogUtil.info(logger, "Checking target file ( {0} )", samFile.toAbsolutePath().normalize().toString());
			
			// SAM 파일 읽어서 리스트에 담기
			time = System.currentTimeMillis();
			LogUtil.info(logger, "Reading sam list file");

			List<String[]> samFileDataList = new ArrayList<>();		
			try {
				samFileDataList = Files.readAllLines(samFile).stream().map(t -> t.split(Pattern.quote(Config.getConfig("INPUT.DELIMITER")))).collect(Collectors.toList());
			} catch (IOException e) {
				e.printStackTrace();
				LogUtil.error(logger,"Error occurred during reading sam list file ({0})", e.getMessage());
				return;
			}

			LogUtil.info(logger, "Completed reading sam list file ( {0}s )", CommonUtil.getTimeElapsed(time));
			
			// 커넥션 객체 만들기
			time = System.currentTimeMillis();
			Connection con = null;
			try {
				LogUtil.info(logger, "Creating connection of database(from) ( {0} / {1} / {2} )", Config.getConfig("DB.URL"), Config.getConfig("DB.USER"), Config.getConfig("DB.PASSWORD"));
				con = getConnection(Config.getConfig("DB.URL"), Config.getConfig("DB.USER"), Config.getConfig("DB.PASSWORD"));
			} catch (NullPointerException | SQLException e) {
				e.printStackTrace();
				LogUtil.error(logger, "Error occurred during create connection of database ({0})", e.getMessage());
				return;
			}

			LogUtil.info(logger, "Completed creating connection of database ( {0}s )", CommonUtil.getTimeElapsed(time));

			// TO-BE 데이터 삽입하기
			int count = 0;
			try {
				if (samFileDataList.size() != 0) {
					LogUtil.info(logger, "Inserting data of database");
					count = insert(con, Config.getConfig("DB.QUERY"), samFileDataList);
				}
			} catch (NullPointerException | SQLException e) {
				e.printStackTrace();
				LogUtil.error(logger, "Error occurred during insert of database ({0})", e.getMessage());
				return;
			} finally {
				if (con != null){
					try {
						con.close();
						con = null;
					} catch (SQLException e) {
						e.printStackTrace();
						LogUtil.error(logger, "Error occurred during close of database connection ({0})", e.getMessage());
						return;
					}
				}
			}

			totalCount += count;

			LogUtil.info(logger, "Completed {0} insertion data ( {1}s )", count, CommonUtil.getTimeElapsed(time));
		}
		
		LogUtil.info(logger, "Completed all {0} processing", totalCount);
	}
	
	/**
	 * 삽입 하기
	 * @param con
	 * @param query
	 * @param paramList
	 * @return 데이터 삽입 후 결과
	 * @throws SQLException
	 */
	private int insert(Connection con, String query, List<String[]> paramList) throws SQLException {
		int count = 0;
		PreparedStatement statement = null;

		try {
			statement = con.prepareStatement(query);
			statement.setQueryTimeout(Config.getIntConfig("DB.TIMEOUT"));

			for (int i=0; i<paramList.size(); i++) {
				String[] param = paramList.get(i);

				for (int j=0; j<param.length; j++)
					statement.setString(j+1, param[j]);

				statement.addBatch();
				statement.clearParameters();

				if ((i % 50) == 0) {
					int[] ret = statement.executeBatch();
					statement.clearBatch();
					con.commit();
					
					for (int j=0; j<ret.length; j++) {
						if (ret[j] == Statement.SUCCESS_NO_INFO)
							count = count + 1;
						else if (ret[j] == Statement.EXECUTE_FAILED)
							count = count + 0;
						else
							count = count + ret[j];
					}
				}
			}

			int[] ret = statement.executeBatch();
			statement.clearBatch();
			con.commit();
			
			for (int j=0; j<ret.length; j++) {
				if (ret[j] == Statement.SUCCESS_NO_INFO)
					count = count + 1;
				else if (ret[j] == Statement.EXECUTE_FAILED)
					count = count + 0;
				else
					count = count + ret[j];
			}
		} catch (SQLException e) {
			con.rollback();

			throw e;
		} finally {
			if (statement != null) {
				statement.close();
				statement = null;
			}
		}

		return count;
	}

	/**
	 * 커넥션 객체 만들기
	 * @param url
	 * @param user
	 * @param password
	 * @return Connection 객체
	 * @throws SQLException
	 */
	private Connection getConnection(String url, String user, String password) throws SQLException {
		Connection con = DriverManager.getConnection(url, user, password);
		con.setAutoCommit(false);
		return con;
	}
}
