package com.github.hakko.musiccabinet.dao.jdbc;

import static com.github.hakko.musiccabinet.dao.util.PostgreSQLUtil.getParameters;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.object.BatchSqlUpdate;

import com.github.hakko.musiccabinet.dao.AlbumInfoDao;
import com.github.hakko.musiccabinet.domain.model.music.Album;
import com.github.hakko.musiccabinet.domain.model.music.AlbumInfo;
import com.github.hakko.musiccabinet.domain.model.music.Artist;
import com.github.hakko.musiccabinet.log.Logger;

public class JdbcAlbumInfoDao implements AlbumInfoDao, JdbcTemplateDao {

	private JdbcTemplate jdbcTemplate;

	private static final Logger LOG = Logger.getLogger(JdbcAlbumInfoDao.class);
	
	@Override
	public void createAlbumInfo(List<AlbumInfo> albumInfos) {
		if (albumInfos.size() > 0) {
			clearImportTable();
			batchInsert(albumInfos);
			updateLibrary();
		}
	}
	
	private void clearImportTable() {
		jdbcTemplate.execute("delete from music.albuminfo_import");
	}
	
	private void batchInsert(List<AlbumInfo> albumInfos) {
		String sql = "insert into music.albuminfo_import (artist_name, album_name, smallimageurl, mediumimageurl, largeimageurl, extraLargeimageurl, listeners, playcount) values (?,?,?,?,?,?,?,?)";
		BatchSqlUpdate batchUpdate = new BatchSqlUpdate(jdbcTemplate.getDataSource(), sql);
		batchUpdate.setBatchSize(1000);
		batchUpdate.declareParameter(new SqlParameter("artist_name", Types.VARCHAR));
		batchUpdate.declareParameter(new SqlParameter("album_name", Types.VARCHAR));
		batchUpdate.declareParameter(new SqlParameter("smallimageurl", Types.VARCHAR));
		batchUpdate.declareParameter(new SqlParameter("mediumimageurl", Types.VARCHAR));
		batchUpdate.declareParameter(new SqlParameter("largeimageurl", Types.VARCHAR));
		batchUpdate.declareParameter(new SqlParameter("extraLargeimageurl", Types.VARCHAR));
		batchUpdate.declareParameter(new SqlParameter("listeners", Types.INTEGER));
		batchUpdate.declareParameter(new SqlParameter("playcount", Types.INTEGER));
		
		for (AlbumInfo ai : albumInfos) {
			batchUpdate.update(new Object[]{ai.getAlbum().getArtist().getName(), ai.getAlbum().getName(),
					ai.getSmallImageUrl(), ai.getMediumImageUrl(), ai.getLargeImageUrl(),
					ai.getExtraLargeImageUrl(), ai.getListeners(), ai.getPlayCount()});
		}
		batchUpdate.flush();
	}

	private void updateLibrary() {
		jdbcTemplate.execute("select music.update_albuminfo_from_import()");
	}

	@Override
	public AlbumInfo getAlbumInfo(int albumId) {
		String sql = 
				"select ai.largeimageurl, ai.extralargeimageurl from music.albuminfo ai" + 
				" where ai.album_id = " + albumId;
		AlbumInfo albumInfo = null;
		
		try {
			albumInfo = jdbcTemplate.queryForObject(sql, 
					new RowMapper<AlbumInfo>() {
				@Override
				public AlbumInfo mapRow(ResultSet rs, int rowNum)
						throws SQLException {
					AlbumInfo ai = new AlbumInfo();
					ai.setLargeImageUrl(rs.getString(1));
					ai.setExtraLargeImageUrl(rs.getString(2));
					return ai;
				}

			});
		} catch (DataAccessException e) {
			LOG.warn("There's no album info for album " + albumId, e);
		}

		return albumInfo;
	}
	
	@Override
	public AlbumInfo getAlbumInfo(final Album album) {
		String sql = 
				"select ai.smallimageurl, ai.mediumimageurl, ai.largeimageurl, ai.extralargeimageurl, ai.listeners, ai.playcount from music.albuminfo ai" + 
				" inner join music.album alb on ai.album_id = alb.id" +
				" inner join music.artist art on alb.artist_id = art.id" +
				" where alb.album_name = upper(?) and art.artist_name = upper(?)";
		
		AlbumInfo albumInfo = jdbcTemplate.queryForObject(sql, 
				new Object[]{album.getName(), album.getArtist().getName()}, 
				new RowMapper<AlbumInfo>() {
			@Override
			public AlbumInfo mapRow(ResultSet rs, int rowNum)
					throws SQLException {
				AlbumInfo ai = new AlbumInfo();
				ai.setAlbum(album);
				ai.setSmallImageUrl(rs.getString(1));
				ai.setMediumImageUrl(rs.getString(2));
				ai.setLargeImageUrl(rs.getString(3));
				ai.setExtraLargeImageUrl(rs.getString(4));
				ai.setListeners(rs.getInt(5));
				ai.setPlayCount(rs.getInt(6));
				return ai;
			}
			
		});

		return albumInfo;
	}
	
	@Override
	public List<AlbumInfo> getAlbumInfosForArtist(final Artist artist) {
		String sql = 
				"select alb.album_name_capitalization, ai.mediumimageurl, "
				+ " ai.largeimageurl, ai.extralargeimageurl from music.albuminfo ai"
				+ " inner join music.album alb on ai.album_id = alb.id"
				+ " inner join music.artist art on alb.artist_id = art.id"
				+ " where art.artist_name = upper(?)";

		List<AlbumInfo> albums = jdbcTemplate.query(sql, 
				new Object[]{artist.getName()}, 
				new RowMapper<AlbumInfo>() {
			@Override
			public AlbumInfo mapRow(ResultSet rs, int rowNum)
					throws SQLException {
				AlbumInfo ai = new AlbumInfo();
				String albumName = rs.getString(1);
				ai.setMediumImageUrl(rs.getString(2));
				ai.setLargeImageUrl(rs.getString(3));
				ai.setExtraLargeImageUrl(rs.getString(4));
				ai.setAlbum(new Album(artist, albumName));
				return ai;
			}
		});

		return albums;
	}

	@Override
	public Map<String, AlbumInfo> getAlbumInfosForPaths(List<String> paths) {
		String sql = 
				"select alb.album_name_capitalization, ai.mediumimageurl,"
				+ " ai.largeimageurl, ai.extralargeimageurl, md.path from music.albuminfo ai"
				+ " inner join music.album alb on ai.album_id = alb.id"
				+ " inner join library.musicdirectory md on md.album_id = alb.id"
				+ " where md.path in (" + getParameters(paths.size()) + ")";

		final Map<String, AlbumInfo> albumInfos = new HashMap<String, AlbumInfo>();
		try {
			jdbcTemplate.query(sql, paths.toArray(), new RowCallbackHandler() {
				@Override
				public void processRow(ResultSet rs) throws SQLException {
					AlbumInfo ai = new AlbumInfo();
					String albumName = rs.getString(1);
					ai.setMediumImageUrl(rs.getString(2));
					ai.setLargeImageUrl(rs.getString(3));
					ai.setExtraLargeImageUrl(rs.getString(4));
					ai.setAlbum(new Album(albumName));
					String path = rs.getString(5);
					albumInfos.put(path, ai);
				}
			});
		} catch (DataAccessException e) {
			LOG.warn("Could not fetch album infos for paths " + paths + "!", e);
		}
		
		return albumInfos;
	}
	
	@Override
	public List<Album> getAlbumsWithoutInfo() {
		String sql = "select art.artist_name_capitalization, alb.album_name_capitalization from"
				+ " library.musicdirectory md"
				+ " inner join music.album alb on md.album_id = alb.id"
				+ " inner join music.artist art on alb.artist_id = art.id"
				+ " where not exists ("
				+ " select 1 from music.albuminfo ai "
				+ " inner join music.album alb on ai.album_id = alb.id where alb.artist_id = art.id)";

		List<Album> albums = jdbcTemplate.query(sql, new RowMapper<Album>() {
			@Override
			public Album mapRow(ResultSet rs, int rowNum) throws SQLException {
				return new Album(rs.getString(1), rs.getString(2));
			}
		});

		return albums;
	}

	@Override
	public JdbcTemplate getJdbcTemplate() {
		return jdbcTemplate;
	}
	
	public void setDataSource(DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}

}