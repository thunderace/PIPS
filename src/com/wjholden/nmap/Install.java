package com.wjholden.nmap;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.content.Context;
import android.content.res.Resources;
import android.os.Message;

/**
 * Now that I know about this cool thing called a
 * DigestOutputStream, this class is pretty solid, having the
 * potential to catch errors and all.
 * @author John
 *
 */
class Install implements Runnable {

	private static final int BUFFER_SIZE = 8192;

	private class InstallerBinary {
		public transient String filename;
		public transient int files[];
		public transient boolean executable;

		public InstallerBinary(final String filename, final int files[], 
				final boolean executable) {
			this.filename = filename;
			this.files = files.clone();
			this.executable = executable;
		}
	}

	private final transient InstallerBinary installerBinaries[] = {
			new InstallerBinary("nmap", new int[] { R.raw.nmap_aa,
					R.raw.nmap_ab, R.raw.nmap_ac }, true),
			new InstallerBinary("nmap-os-db", new int[] { R.raw.nmap_os_db_aa,
					R.raw.nmap_os_db_ab, R.raw.nmap_os_db_ac }, false),
			new InstallerBinary("nmap-payloads",
					new int[] { R.raw.nmap_payloads }, false),
			new InstallerBinary("nmap-protocols",
					new int[] { R.raw.nmap_protocols }, false),
			new InstallerBinary("nmap-rpc", new int[] { R.raw.nmap_rpc }, false),
			new InstallerBinary("nmap-service-probes",
					new int[] { R.raw.nmap_service_probes_aa,
							R.raw.nmap_service_probes_ab }, false),
			new InstallerBinary("nmap-services",
					new int[] { R.raw.nmap_services }, false),
			new InstallerBinary("nmap-mac-prefixes",
					new int[] { R.raw.nmap_mac_prefixes }, false) };

	private final transient String binaryDirectory;
	private final transient Resources appResources;
	private final transient boolean hasRoot;

	/**
	 * 
	 * @param context Context of the activity launching this installer.
	 * @param binaryDirectory Location to save binaries.
	 * @param hasRoot Does user have root access or not.
	 */
	public Install(final Context context, final String binaryDirectory,
			final boolean hasRoot) {
		super();
		this.appResources = context.getResources();
		this.binaryDirectory = binaryDirectory;
		this.hasRoot = hasRoot;
	}

	private void deleteExistingFile(final File myFile) {
		if (myFile.exists()) {
			PipsError.log(myFile.getAbsolutePath() + " exists. Deleting...");
			if (myFile.delete()) {
				PipsError.log("...deleted.");
			} else {
				PipsError.log("...unable to delete.");
			}
		}
	}

	private MessageDigest writeNewFile(final File myFile, final int fileResources[]) {
		final byte[] buf = new byte[BUFFER_SIZE];

		DigestOutputStream out;
		MessageDigest md5 = null;
		try {
			md5 = MessageDigest.getInstance("MD5");
			out = new DigestOutputStream(new FileOutputStream(myFile), md5);
			for (int resource : fileResources) {
				final InputStream inputStream = appResources
						.openRawResource(resource);
				while (inputStream.read(buf) > 0) {
					out.write(buf);
				}
				inputStream.close();
			}
			out.close();
		} catch (FileNotFoundException e) {
			PipsError.log(e);
		} catch (IOException e) {
			PipsError.log(e);
		} catch (NoSuchAlgorithmException e) {
			PipsError.log(e);
		}
		
		return md5;
	}

	private void setExecutable(final File myFile) {
		final String shell = hasRoot ? "su" : "sh";
		try {
			final Process process = Runtime.getRuntime().exec(shell);
			final DataOutputStream outputStream = new DataOutputStream(
					process.getOutputStream());
			final BufferedReader inputStream = new BufferedReader(
					new InputStreamReader(process.getInputStream()),
					BUFFER_SIZE);
			final BufferedReader errorStream = new BufferedReader(
					new InputStreamReader(process.getErrorStream()),
					BUFFER_SIZE);

			outputStream.writeBytes("cd " + this.binaryDirectory + "\n");

			if (hasRoot) {
				outputStream.writeBytes("chown root.root * \n");
				PipsError.log("chown root.root *");
			}

			outputStream.writeBytes("chmod 555 " + myFile.getAbsolutePath()
					+ " \n");
			PipsError.log("chmod 555 " + myFile.getAbsolutePath());

			outputStream
					.writeBytes("chmod 777 " + this.binaryDirectory + " \n");
			PipsError.log("chmod 777 " + this.binaryDirectory + " \n");

			outputStream.writeBytes("exit\n");

			final StringBuilder feedback = new StringBuilder();
			String input, error;
			while ((input = inputStream.readLine()) != null) {
				feedback.append(input);
			}
			while ((error = errorStream.readLine()) != null) {
				feedback.append(error);
			}

			final String chmodResult = feedback.toString();
			PipsError.log(chmodResult);

			outputStream.close();
			inputStream.close();
			errorStream.close();
			process.waitFor();
			process.destroy();

			if (chmodResult.length() > 0) {
				Message.obtain(MainActivity.handler, Constants.INSTALL_ERROR,
						chmodResult).sendToTarget();
			}
		} catch (IOException e) {
			PipsError.log(e);
			Message.obtain(MainActivity.handler, Constants.INSTALL_ERROR,
					e.toString()).sendToTarget();
		} catch (InterruptedException e) {
			PipsError.log(e);
			Message.obtain(MainActivity.handler, Constants.INSTALL_ERROR,
					e.toString()).sendToTarget();
		}
	}

	public synchronized void run() {
		PipsError.log(Thread.currentThread().getName());
		Message.obtain(MainActivity.handler, Constants.PROGRESS_DIALOG_START,
				(Object) "Installing Nmap binaries...").sendToTarget();
		File myFile;

		for (InstallerBinary install : installerBinaries) {
			final String filename = binaryDirectory + install.filename;

			Message.obtain(MainActivity.handler,
					Constants.PROGRESS_DIALOG_CHANGE_TEXT,
					(Object) "Installing " + install.filename).sendToTarget();
			myFile = new File(filename);

			deleteExistingFile(myFile);

			MessageDigest md5 = writeNewFile(myFile, install.files);
			
			if (install.executable) {
				setExecutable(myFile);
			}
			
			PipsError.log("Installed " + install.filename + " (MD5 hash: " + getHash(md5) + ").");
		}
		MainActivity.handler
				.sendEmptyMessage(Constants.PROGRESS_DIALOG_DISMISS);
		MainActivity.handler.sendEmptyMessage(Constants.INSTALL_COMPLETE);
	}
	
	private String getHash (MessageDigest digest) {
		StringBuffer hexString = new StringBuffer();
		byte[] hash = digest.digest();

		for (int i = 0; i < hash.length; i++) {
			if ((0xff & hash[i]) < 0x10) {
				hexString.append("0" + Integer.toHexString((0xFF & hash[i])));
			} else {
				hexString.append(Integer.toHexString(0xFF & hash[i]));
			}
		}
		
		return hexString.toString();
	}
}
