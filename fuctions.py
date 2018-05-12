import os
import shutil
from shutil import copyfile

import parseLog
import parseLogCopy2

import constants
from download_shared_file import download_file_from_google_drive
from prepare_jaid_properties_file import generate_new_properties_file_from_old, \
    generate_intro_class_java_properties_file
from read_fj_output import read_new_jaid_result, append_csv
from utils import unzip, get_icj_bug_id, split_icj_bug_id, get_max_backup_folder, is_desired_size, is_desired_name, \
    is_modified_file


# =====================================================================================================
# Operation Function Definitions
# =====================================================================================================
def show_config(exp_config):
    print("Config: ", str(exp_config))


def prepare_all_d4j(exp_config):
    for candidate in constants.D4J_CANDIDATES.keys():
        repo_path = os.path.join(exp_config.get_working_path(), candidate)
        prepare_d4j_bv(exp_config, repo_path)


def download_all_d4j(exp_config):
    for can_key in constants.D4J_CANDIDATES.keys():
        zip_path = os.path.join(exp_config.get_working_path(), can_key + constants.ZIP_SUFFIX)
        if not os.path.exists(zip_path):
            download_file_from_google_drive(constants.D4J_CANDIDATES[can_key], zip_path)


def download_if_not_exist(repository_path):
    if not os.path.exists(repository_path):
        zip_repo_path = repository_path + constants.ZIP_SUFFIX
        if not os.path.exists(zip_repo_path):
            # download the specific project and store in 'zip_repo_path'
            zip_file = os.path.split(zip_repo_path)
            zip_file_name = zip_file[1][0:len(zip_file[1]) - len(constants.ZIP_SUFFIX)]
            if zip_file_name in constants.D4J_CANDIDATES.keys():
                download_file_from_google_drive(constants.D4J_CANDIDATES[zip_file_name], zip_repo_path)
            elif zip_file_name in constants.D4J_OLD_CANDIDATES.keys():
                download_file_from_google_drive(constants.D4J_OLD_CANDIDATES[zip_file_name], zip_repo_path)
            elif zip_file_name in constants.ICJ_CANDIDATES.keys():
                download_file_from_google_drive(constants.ICJ_CANDIDATES[zip_file_name], zip_repo_path)
            else:
                print('download ' + zip_file_name + ' fail.')
                return
        unzip(zip_repo_path, repository_path)


def prepare_d4j_bv(exp_config, repository_path):
    drive, repo = os.path.split(repository_path)
    download_if_not_exist(repository_path)
    property_file_path = generate_new_properties_file_from_old(repository_path, exp_config)
    print('The buggy program ' + repo + ' is ready. Program arguments of JAID running configuration is:')
    print(constants.JAID_ARG_PROPERTY + ' ' + property_file_path)
    return property_file_path


def read_d4j_bv(repository_path):
    for inner_inner_folder in os.listdir(repository_path):
        if inner_inner_folder == constants.OUTPUT_FOLDER_NAME:  # find the experiment output in repo
            jaid_output_dir = os.path.join(repository_path, inner_inner_folder)
            read_new_jaid_result(jaid_output_dir)


def run_icj_cluster(exp_config, repository_cluster_path):  # walk the repository_cluster_path to get repository_path(bv)
    download_if_not_exist(repository_cluster_path)
    for folder in os.listdir(repository_cluster_path):
        if not folder == 'reference':
            for inner_folder in os.listdir(os.path.join(repository_cluster_path, folder)):
                repository_path = os.path.join(repository_cluster_path, folder, inner_folder)
                bv_properties = prepare_icj_bv(exp_config, repository_path)
                if bv_properties:
                    run_(exp_config, bv_properties)


def pre_icj_bv(exp_config, repository_cluster_path,
               bug_id):  # walk the repository_cluster_path to get repository_path(bv)
    download_if_not_exist(repository_cluster_path)
    author_id, bv = split_icj_bug_id(bug_id)
    for folder in os.listdir(repository_cluster_path):
        if folder.startswith(author_id):
            repository_path = os.path.join(repository_cluster_path, folder, bv)
            bv_properties = prepare_icj_bv(exp_config, repository_path)
            return bv_properties


def run_icj_bv(exp_config, repository_cluster_path, bug_id):
    bv_properties = pre_icj_bv(exp_config, repository_cluster_path, bug_id)
    if bv_properties:
        run_(exp_config, bv_properties)


def read_icj_cluster(exp_config, repo_name):  # walk the repository_cluster_path to get repository_path(bv)
    repository_cluster_path = os.path.join(exp_config.get_working_path(), repo_name)
    csv_path = os.path.join(exp_config.get_working_path(), constants.ICJ_RESULT_FILE_NAME)
    for author_id_folder in os.listdir(repository_cluster_path):
        if not author_id_folder == 'reference':
            for version_id_folder in os.listdir(os.path.join(repository_cluster_path, author_id_folder)):
                repository_path = os.path.join(repository_cluster_path, author_id_folder, version_id_folder)
                for inner_inner_folder in os.listdir(repository_path):
                    if inner_inner_folder == constants.OUTPUT_FOLDER_NAME:  # find the experiment output in repo
                        jaid_output_dir = os.path.join(repository_path, inner_inner_folder)
                        result = read_new_jaid_result(jaid_output_dir)
                        if result:
                            csv_row = [get_icj_bug_id(repo_name, author_id_folder, version_id_folder)]
                            csv_row.extend(result)
                            append_csv(csv_path, csv_row)


def read_icj(exp_config, repo_name, bug_id):
    repository_cluster_path = os.path.join(exp_config.get_working_path(), repo_name)
    author_id, bv = split_icj_bug_id(bug_id)
    csv_path = os.path.join(exp_config.get_working_path(), constants.ICJ_RESULT_FILE_NAME)
    for author_id_folder in os.listdir(repository_cluster_path):
        if author_id_folder.startswith(author_id):
            repository_path = os.path.join(repository_cluster_path, author_id_folder, bv)
            for inner_inner_folder in os.listdir(repository_path):
                if inner_inner_folder == constants.OUTPUT_FOLDER_NAME:  # find the experiment output in repo
                    jaid_output_dir = os.path.join(repository_path, inner_inner_folder)
                    result = read_new_jaid_result(jaid_output_dir)
                    if result:
                        csv_row = [get_icj_bug_id(repo_name, author_id_folder, bv)]
                        csv_row.extend(result)
                        append_csv(csv_path, csv_row)


def prepare_icj_bv(exp_config, bv_path):  # prepare properties file for one buggy_version_path(bv)
    if os.path.exists(bv_path):
        property_file_path = generate_intro_class_java_properties_file(bv_path, exp_config)
        # print('The buggy program ' + repo + ' is ready. Program arguments of JAID running configuration is:')
        if property_file_path:
            print(constants.JAID_ARG_PROPERTY + ' ' + property_file_path)
        return property_file_path


def run_(exp_config, properties_file_path):
    command = repr(os.path.normpath(os.path.join(exp_config.JDKDir, 'bin', 'java'))).replace('\'',
                                                                                             '"') + ' -jar ' + \
              exp_config.get_jaid_path() + ' ' + constants.JAID_ARG_PROPERTY + ' ' + properties_file_path + ' ' + '1 "FL" 1 1'
    print('EXE COMMAND: ' + command)
    os.system(command)
    

def run_(exp_config, properties_file_path,repo_path):

    command = repr(os.path.normpath(os.path.join(exp_config.JDKDir, 'bin', 'java'))).replace('\'',

         '"') + ' -jar ' + \
              exp_config.get_jaid_path() + ' ' + constants.JAID_ARG_PROPERTY + ' ' + properties_file_path + ' ' + '2'
    print('EXE COMMAND: ' + command)
    os.system(command)
    command = 'cp -r '+ properties_file_path.split('/local_jaid.properties')[0]+'/jaid_output '+ properties_file_path.split('/local_jaid.properties')[0]+'/jaid_output'+'republic10r11'
    print(command)
    os.system(command)
    projectName = properties_file_path.split('/')[-2]
    #logPath = properties_file_path.split('/local_jaid.properties')[0]+'/jaid_output'+'1FL11/jaid_output/'+'location_sort_random.log'
    #logPathOri = properties_file_path.split('/local_jaid.properties')[0]+'/jaid_output'+'/'+'location_sort_given_number.log'
    #parseLogCopy2.parseAllProcessLog(projectName,logPath,'FL',logPathOri)

    






def get_repo_path(exp_config, repo_name, bug_id):
    repo = constants.repositories[repo_name.lower()] + bug_id
    return repo, os.path.join(exp_config.get_working_path(), repo)


def rm_last_max_backup_folder(backup_dir):
    o_backup_id = get_max_backup_folder(backup_dir)
    if o_backup_id is not None:
        o_backup_dir = os.path.join(backup_dir, constants.BACKUP_FOLDER_NAME + str(o_backup_id))
        shutil.rmtree(o_backup_dir)
        print(o_backup_dir + ' is removed')


def tracking_all_results(working_dir, backup_dir):  # Find all experiment results in the working dir and backup them
    o_backup_id = get_max_backup_folder(backup_dir)
    o_backup_dir = None
    if o_backup_id is not None:
        o_backup_dir = os.path.join(backup_dir, constants.BACKUP_FOLDER_NAME + str(o_backup_id))
        n_backup_dir = os.path.join(backup_dir, constants.BACKUP_FOLDER_NAME + str(int(o_backup_id) + 1))
    else:
        n_backup_dir = os.path.join(backup_dir, constants.BACKUP_FOLDER_NAME + '0')
    os.mkdir(n_backup_dir)
    print('Backup stored in folder: '+n_backup_dir)

    for x_repo in os.listdir(working_dir):
        x_repo_dir = os.path.join(working_dir, x_repo)
        if os.path.isdir(x_repo_dir):
            print('Checking ' + x_repo + ' ...')
            if x_repo in constants.D4J_CANDIDATES.keys():  # backup output for d4j bugs
                back_up_output(x_repo_dir, x_repo, o_backup_dir, n_backup_dir)
            elif x_repo in constants.ICJ_CANDIDATES.keys():
                pass
            else:
                print('folder ' + x_repo + ' is not a desired repository.')
    print('tracking_all_results is done')


def back_up_output(repository_path, repo_folder, o_backup_dir, n_backup_dir):
    for folder in os.listdir(repository_path):
        if folder == constants.OUTPUT_FOLDER_NAME:  # find the experiment output in repo
            jaid_output_dir = os.path.join(repository_path, folder)
            print('Copying ' + repository_path + ' ...')

            for exp_output in os.listdir(jaid_output_dir):
                src = os.path.join(jaid_output_dir, exp_output)
                dst = os.path.join(n_backup_dir, repo_folder)

                if is_desired_size(src) and is_desired_name(src):
                    if not os.path.isdir(dst):
                        os.mkdir(dst)
                    if o_backup_dir is None or is_modified_file(os.path.join(o_backup_dir, repo_folder),
                                                                jaid_output_dir, exp_output):
                        copyfile(src, os.path.join(dst, exp_output))
                    # else:
                    #     os.link(os.path.join(os.path.join(o_backup_dir, repo_folder), exp_output),
                    #             os.path.join(dst, exp_output))
            break


def print_available_bv():
    # print('Available D4J repositories name and corresponding identifier:')
    # print(constants.repositories)
    print('All available project and buggy ids:')
    print('<REPO_NAME>: <BUG_VERSION_1>,<BUG_VERSION2>,...')
    for project in constants.valid_bvs.keys():
        print(project + ' : ' + ','.join(sorted(set(constants.valid_bvs[project]))))
        print('\n')
    print('=' * 10)
    print('Defects4J <BUG_ID> format:' + constants.D4J_BUGID_FORMAT + ' (e.g.,Lang33)')
    print('IntroClassJava <BUG_ID> format:' + constants.ICJ_BUGID_FORMAT + ' (e.g.,checksum-e23b9_005)')
    print('=' * 10)
    print('Example for running JAID to fix a buggy program:')
    print('$ python3 <PATH_TO_jaid_exp_pre>/src/main.py ' + constants.COMMAND_RUN + ' Lang33,checksum-e23b9_005')
    # print('All available IntroClassJava repositories:')
    # print(','.join(constants.ICJ_CANDIDATES.keys()))
    print('Example for reading JAID\'s output of fixing a buggy program:')
    print('$ python3 <PATH_TO_jaid_exp_pre>/src/main.py ' + constants.COMMAND_READ + ' Lang33,checksum-e23b9_005')
